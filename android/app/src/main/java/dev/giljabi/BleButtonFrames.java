package dev.giljabi;

import java.util.function.Consumer;

/** UART is a byte stream: BLE packet boundaries are not command boundaries. */
final class BleButtonFrames {
    private final StringBuilder line=new StringBuilder();private boolean discard;
    void reset(){line.setLength(0);discard=false;}
    void accept(byte[] data,Consumer<String> receiver){
        if(data==null)return;
        for(byte b:data){int c=b&255;if(c=='\r')continue;
            if(c=='\n'){String value=line.toString();reset();if(value.equals("ASSIST"))receiver.accept(value);}
            else if(c<32||c>126){discard=true;line.setLength(0);}
            else if(!discard){if(line.length()>=31){discard=true;line.setLength(0);}else line.append((char)c);}
        }
    }
}
