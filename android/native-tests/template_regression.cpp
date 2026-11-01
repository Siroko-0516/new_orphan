#include "llama.h"
#include "chat.h"
#include "local_chat_prompt.h"
#include <iostream>
#include <stdexcept>
int main(int argc,char **argv){
 if(argc!=2)return 2;
 llama_backend_init();llama_model_params p=llama_model_default_params();p.vocab_only=true;
 llama_model *model=llama_model_load_from_file(argv[1],p);if(!model)return 3;
 auto tmpl=common_chat_templates_init(model,"");common_chat_msg sys;sys.role="system";sys.content="Describe only visible objects.";
 bool reproduced=false;
 try{common_chat_format_single(tmpl.get(),{},sys,false,true);}
 catch(const std::invalid_argument &e){reproduced=std::string(e.what()).find("Unable to generate parser for this template")!=std::string::npos;std::cout<<"OLD_ROUTE_EXCEPTION="<<e.what()<<"\n";}
 if(!reproduced)throw std::runtime_error("Original automatic-parser failure was not reproduced");
 for(int i=0;i<3;i++){
  std::string user="<__media__>\nDescribe the photo briefly. "+std::to_string(i);
  std::string output=giljabi::render_turn(tmpl.get(),sys.content,user,false);
  if(output.find(sys.content)==std::string::npos||output.find(user)==std::string::npos)throw std::runtime_error("Full turn lost content");
  if(output.find("<|im_start|>user")==std::string::npos||output.find("<|im_start|>assistant")==std::string::npos)throw std::runtime_error("Role framing missing");
  std::cout<<"FIXED_FULL_TURN="<<i<<" PROMPT_BYTES="<<output.size()<<"\n";
 }
 tmpl.reset();llama_model_free(model);llama_backend_free();std::cout<<"TEMPLATE_REGRESSION_PASS\n";
}
