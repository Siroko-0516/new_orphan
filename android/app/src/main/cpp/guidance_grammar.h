#pragma once
// Syntax constraint only. Semantic grounding and speech eligibility stay in Java.
static constexpr const char *GUIDANCE_JSON_GRAMMAR=R"gbnf(
root ::= "{" ws "\"speak\"" ws ":" ws (silent | spoken) ws "}" ws
silent ::= "false" ws "," ws "\"message\"" ws ":" ws "\"\"" ws "," ws "\"urgency\"" ws ":" ws "\"none\""
spoken ::= "true" ws "," ws "\"message\"" ws ":" ws string ws "," ws "\"urgency\"" ws ":" ws ("\"notice\"" | "\"urgent\"")
string ::= "\"" char{0,200} "\""
char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" hex hex hex hex)
hex ::= [0-9a-fA-F]
ws ::= [ \t\n\r]*
)gbnf";
