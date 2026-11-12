#pragma once
#include <algorithm>
#include <functional>
#include <set>
#include <vector>
#include <nlohmann/json.hpp>
#include <stdexcept>
#include "common.h"
#include <string>
#include "chat.h"

namespace signal_grove {
inline common_chat_templates_inputs local_chat_inputs(const std::vector<std::string> & helper_ids = {}) {
    common_chat_templates_inputs input;
    // Let the model's own template open its normal reasoning channel. Some
    // models continue reasoning even after an empty, pre-closed thinking block.
    input.enable_thinking = true;
    input.reasoning_format = COMMON_REASONING_FORMAT_AUTO;
    input.use_jinja = true;
    if (!helper_ids.empty()) {
        if (helper_ids.size() > 5) throw std::length_error("local helper choices bound");
        std::set<std::string> unique;
        for (const auto & id : helper_ids) {
            if (id.empty() || id.size() > 64 || !unique.insert(id).second)
                throw std::runtime_error("invalid local helper choice");
            for (char c : id) if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_'))
                throw std::runtime_error("invalid local helper choice");
        }
        // Enumerate every permitted ordered selection, not generated instructions.
        // This also enforces uniqueness with schema-converter implementations
        // that do not support the JSON Schema uniqueItems keyword.
        nlohmann::ordered_json alternatives = nlohmann::ordered_json::array();
        std::vector<std::string> chosen;
        std::function<void()> enumerate = [&] {
            if (!chosen.empty()) alternatives.push_back(chosen);
            if (chosen.size() == 3) return;
            for (const auto & id : helper_ids) if (std::find(chosen.begin(), chosen.end(), id) == chosen.end()) {
                chosen.push_back(id); enumerate(); chosen.pop_back();
            }
        };
        enumerate();
        input.json_schema = nlohmann::ordered_json{{"enum", alternatives}}.dump();
        if (input.json_schema.size() > 32768) throw std::length_error("local helper schema bound");
    }
    return input;
}

// Bound hidden reasoning within the caller's existing total token allowance.
// Use the template's declared markers and upstream UTF-8-aware budget sampler.
inline void apply_local_chat_sampling(const common_chat_session & session, const llama_vocab * vocab,
                                     common_params_sampling & sampling, int total_tokens) {
    session.apply_sampling(sampling);
    if (session.thinking_start_tag().empty() || session.thinking_end_tags().empty()) return;
    if (session.thinking_start_tag().size() > 4096) throw std::length_error("local reasoning marker bound");
    sampling.reasoning_budget_start = common_tokenize(vocab, session.thinking_start_tag(), false, true);
    sampling.reasoning_budget_end.clear();
    for (const auto & end : session.thinking_end_tags()) {
        if (end.empty()) continue;
        if (end.size() > 4096) throw std::length_error("local reasoning marker bound");
        auto tokens = common_tokenize(vocab, end, false, true);
        if (!tokens.empty()) sampling.reasoning_budget_end.push_back(std::move(tokens));
    }
    if (sampling.reasoning_budget_start.empty() || sampling.reasoning_budget_end.empty())
        throw std::runtime_error("local reasoning markers unavailable");
    sampling.reasoning_budget_tokens = std::min(64, std::max(0, total_tokens / 4));
    sampling.reasoning_budget_forced = sampling.reasoning_budget_end.front();
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
