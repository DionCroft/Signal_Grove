#pragma once
#include <stdexcept>
#include <string>
#include "chat.h"

namespace signal_grove {
inline common_chat_templates_inputs local_chat_inputs() {
    common_chat_templates_inputs input;
    // Let the model's own template open its normal reasoning channel. Some
    // models continue reasoning even after an empty, pre-closed thinking block.
    input.enable_thinking = true;
    input.reasoning_format = COMMON_REASONING_FORMAT_AUTO;
    input.use_jinja = true;
    return input;
}

// Only the template parser's answer channel may cross the JNI callback. This
// checks cumulative output; it never edits stored history or strips text tags.
class FinalAnswerProjection {
    std::string emitted;
public:
    std::string append(const common_chat_msg & message) {
        if (!message.tool_calls.empty()) throw std::runtime_error("local tools unavailable");
        const auto & content = message.content;
        if (content.size() > 131072) throw std::length_error("local answer bound");
        if (content.compare(0, emitted.size(), emitted) != 0)
            throw std::runtime_error("non-append-only local answer");
        auto delta = content.substr(emitted.size());
        emitted = content;
        return delta;
    }
    bool empty() const { return emitted.empty(); }
};
}
