// SPDX-License-Identifier: MIT
#pragma once
#include "chat.h"
#include <stdexcept>

namespace giljabi {
// MiniCPM-V 4.7 requires a user query even when a system message is supplied.
// Render the complete turn once. We consume prompt text, never a generated tool parser.
inline std::string render_turn(const common_chat_templates *templates,const std::string &system,const std::string &user,bool thinking){
    if(!templates||user.empty())throw std::invalid_argument("A prepared template and user query are required");
    common_chat_templates_inputs inputs;
    inputs.force_pure_content=true;
    inputs.enable_thinking=thinking;
    inputs.add_generation_prompt=true;
    if(!system.empty()){common_chat_msg msg;msg.role="system";msg.content=system;inputs.messages.push_back(msg);}
    common_chat_msg msg;msg.role="user";msg.content=user;inputs.messages.push_back(msg);
    return common_chat_templates_apply(templates,inputs).prompt;
}
}
