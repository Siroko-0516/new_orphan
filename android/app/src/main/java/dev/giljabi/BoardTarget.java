package dev.giljabi;

import java.io.IOException;
import java.util.*;

/** The selected UART endpoint; reconnecting the same endpoint must keep its GATT. */
final class BoardTarget {
    final String address,service,characteristic;
    BoardTarget(String address,String service,String characteristic)throws IOException{
        if(address==null||!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new IOException("BLE 기기를 검색하고 HM-10을 선택해주세요.");
        this.address=address.toUpperCase(Locale.ROOT);
        try{this.service=UUID.fromString(service).toString();this.characteristic=UUID.fromString(characteristic).toString();}
        catch(RuntimeException error){throw new IOException("BLE UUID를 확인해주세요.",error);}
    }
    @Override public boolean equals(Object other){if(!(other instanceof BoardTarget))return false;BoardTarget b=(BoardTarget)other;return address.equals(b.address)&&service.equals(b.service)&&characteristic.equals(b.characteristic);}
    @Override public int hashCode(){return Objects.hash(address,service,characteristic);}
}
