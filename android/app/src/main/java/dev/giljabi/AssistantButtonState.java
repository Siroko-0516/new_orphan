package dev.giljabi;

/** Speech and silence never change turns: recording is submitted by a button. */
final class AssistantButtonState {
    enum Phase { IDLE, RECORDING, FINISHING, READY, WAITING, SPEAKING }
    enum Action { START, FINISH, SEND, IGNORE }
    private Phase phase=Phase.IDLE;
    Phase phase(){return phase;}
    Action press(){
        if(phase==Phase.FINISHING)return Action.IGNORE;
        if(phase==Phase.RECORDING){phase=Phase.FINISHING;return Action.FINISH;}
        if(phase==Phase.READY){phase=Phase.WAITING;return Action.SEND;}
        phase=Phase.RECORDING;return Action.START;
    }
    boolean recorded(){
        if(phase==Phase.FINISHING){phase=Phase.WAITING;return true;}
        if(phase==Phase.RECORDING)phase=Phase.READY;
        return false;
    }
    void waiting(){phase=Phase.WAITING;}
    void speaking(){phase=Phase.SPEAKING;}
    void idle(){phase=Phase.IDLE;}
    boolean enabled(){return phase!=Phase.FINISHING;}
    String label(){
        switch(phase){
            case RECORDING:return "녹음 끝내고 전송";
            case FINISHING:return "녹음 마치는 중…";
            case READY:return "녹음한 말 전송";
            case WAITING:case SPEAKING:return "답변 취소하고 새 녹음";
            default:return "버튼 누르고 말하기";
        }
    }
}
