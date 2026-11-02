export const HM10_SERVICE = '0000ffe0-0000-1000-8000-00805f9b34fb';
export const HM10_CHARACTERISTIC = '0000ffe1-0000-1000-8000-00805f9b34fb';
const COMMANDS = new Set(['TEST\n','OBSTACLE\n']);

/** Optional HM-10 UART bridge. connect() must be called directly from a user click. */
export class BrowserBle {
  static isSupported(bluetooth=globalThis.navigator?.bluetooth) {
    return !!bluetooth && typeof bluetooth.requestDevice === 'function';
  }
  constructor({status=()=>{},bluetooth=globalThis.navigator?.bluetooth}={}) {
    this._status=typeof status==='function'?status:()=>{};
    this._bluetooth=bluetooth;this._generation=0;this._attempt=null;
  }
  get supported() { return BrowserBle.isSupported(this._bluetooth); }
  get connected() { return !!this._attempt?.characteristic && this._attempt.device?.gatt?.connected===true; }
  _current(attempt) { return this._attempt===attempt && this._generation===attempt.generation; }
  _notify(message) {
    try{this._status(message,this.connected);}catch{/* A view callback must not strand a GATT connection. */}
  }
  _cleanup(attempt,disconnect) {
    if(attempt.listener)attempt.device?.removeEventListener?.('gattserverdisconnected',attempt.listener);
    attempt.listener=null;attempt.characteristic=null;
    // A stale promise for the same BluetoothDevice must not close its newer connection.
    if(disconnect&&attempt.connectStarted&&this._attempt?.device!==attempt.device) {
      try{attempt.device?.gatt?.disconnect();}catch{/* Device may already be disconnected. */}
    }
  }
  async connect() {
    if(!this.supported){this._notify('이 브라우저는 BLE 연결을 지원하지 않습니다. Android Chrome 또는 PC Chrome/Edge의 HTTPS 페이지에서 사용하세요.');return false;}
    if(this._attempt){this._notify(this.connected?'HM-10 BLE가 이미 연결돼 있습니다.':'BLE 연결을 준비 중입니다. 기기 선택 창을 확인하세요.');return this.connected;}
    const attempt={generation:++this._generation,device:null,characteristic:null,listener:null,connectStarted:false,writing:false};
    this._attempt=attempt;this._notify('HM-10 BLE 기기를 선택하세요.');
    try {
      // Keep this before any await or automatic callback: the browser requires user activation.
      attempt.device=await this._bluetooth.requestDevice({acceptAllDevices:true,optionalServices:[HM10_SERVICE]});
      if(!this._current(attempt)){this._cleanup(attempt,false);return false;}
      if(!attempt.device?.gatt)throw new Error('NO_GATT');
      attempt.listener=()=>{
        if(!this._current(attempt))return;
        this._attempt=null;this._generation++;this._cleanup(attempt,false);this._notify('HM-10 BLE 연결이 끊겼습니다. 연결 버튼을 눌러 다시 연결하세요.');
      };
      attempt.device.addEventListener?.('gattserverdisconnected',attempt.listener);
      attempt.connectStarted=true;
      const server=await attempt.device.gatt.connect();
      if(!this._current(attempt)){this._cleanup(attempt,true);return false;}
      const service=await server.getPrimaryService(HM10_SERVICE);
      if(!this._current(attempt)){this._cleanup(attempt,true);return false;}
      const characteristic=await service.getCharacteristic(HM10_CHARACTERISTIC);
      if(!this._current(attempt)){this._cleanup(attempt,true);return false;}
      if(!attempt.device.gatt.connected)throw new Error('DISCONNECTED');
      if(!this._writer(characteristic))throw new Error('NO_WRITE');
      attempt.characteristic=characteristic;this._notify('HM-10 BLE 연결 완료. TEST 신호로 보드 수신을 확인하세요.');return true;
    }catch(error){
      if(!this._current(attempt)){this._cleanup(attempt,true);return false;}
      this._attempt=null;this._generation++;this._cleanup(attempt,true);
      if(error?.name==='NotFoundError')this._notify(attempt.device?'선택한 기기에 HM-10 FFE0/FFE1 서비스가 없습니다. 기기를 확인하세요.':'BLE 기기 선택을 취소했습니다.');
      else if(error?.name==='SecurityError'||error?.name==='NotAllowedError')this._notify('블루투스 권한과 HTTPS 연결을 확인한 뒤 연결 버튼을 다시 누르세요.');
      else if(error?.message==='NO_WRITE')this._notify('선택한 BLE 특성이 쓰기를 지원하지 않습니다. HM-10 FFE1 설정을 확인하세요.');
      else this._notify('HM-10 BLE를 연결하지 못했습니다. 전원·거리·다른 기기의 연결 여부를 확인하세요.');
      return false;
    }
  }
  _writer(characteristic) {
    const properties=characteristic?.properties||{};
    if(properties.writeWithoutResponse&&typeof characteristic.writeValueWithoutResponse==='function')return bytes=>characteristic.writeValueWithoutResponse(bytes);
    if(properties.write!==false&&typeof characteristic?.writeValueWithResponse==='function')return bytes=>characteristic.writeValueWithResponse(bytes);
    // Older Web Bluetooth exposes the acknowledged write as writeValue().
    if(properties.write!==false&&typeof characteristic?.writeValue==='function')return bytes=>characteristic.writeValue(bytes);
    return null;
  }
  disconnect() {
    const attempt=this._attempt;this._attempt=null;this._generation++;
    if(attempt)this._cleanup(attempt,true);
    this._notify('HM-10 BLE 연결을 해제했습니다.');
  }
  async send(command) {
    if(!COMMANDS.has(command)){this._notify('지원하지 않는 BLE 신호입니다. TEST 또는 OBSTACLE 신호만 보낼 수 있습니다.');return false;}
    const attempt=this._attempt;
    if(!this.connected||!attempt){this._notify('먼저 HM-10 BLE를 연결하세요.');return false;}
    if(attempt.writing){this._notify('이전 BLE 신호를 처리 중입니다. 새 신호는 대기열에 쌓지 않습니다.');return false;}
    const write=this._writer(attempt.characteristic);if(!write){this._notify('HM-10 BLE 쓰기를 지원하지 않습니다.');return false;}
    attempt.writing=true;
    try{
      await write(new TextEncoder().encode(command));
      if(!this._current(attempt)||!this.connected)return false;
      this._notify(command==='TEST\n'?'TEST 전송 요청 완료 · 보드 수신 확인은 별도입니다.':'OBSTACLE 전송 요청 완료 · 보드 수신 확인은 별도입니다.');return true;
    }catch{
      if(this._current(attempt)){
        if(!attempt.device.gatt.connected){this._attempt=null;this._generation++;this._cleanup(attempt,false);}
        this._notify('BLE 신호를 전송하지 못했습니다. 연결 상태를 확인하세요.');
      }
      return false;
    }finally{attempt.writing=false;}
  }
}
