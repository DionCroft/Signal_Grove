#include "local_chat_output.h"
#include "common.h"
#include "ggml-backend.h"
#include "llama.h"
#include "log.h"
#include "reasoning-budget.h"
#include <cmath>
#include <set>
#include <iostream>
#include <stdexcept>
#include <utility>
#include <vector>

static void require(bool value) { if (!value) throw std::runtime_error("native output assertion"); }
int main(int argc, char ** argv) {
    if (argc != 3) return 2;
    try {
        common_log_set_verbosity_thold(-1);
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        ggml_backend_load_all_from_path(argv[2]); llama_backend_init();
        auto params = llama_model_default_params(); params.vocab_only = true; params.n_gpu_layers = 0;
        auto * model = llama_model_load_from_file(argv[1], params); require(model != nullptr);
        const auto * vocab = llama_model_get_vocab(model);
        auto templates = common_chat_templates_init(model, ""); require(bool(templates));
        const std::string sentence = "The garden is peaceful.";
        const std::vector<std::pair<std::string, std::string>> cases = {
            {sentence + "</think>" + sentence, sentence},
            {"Hidden reasoning.</think>Final answer.", "Final answer."},
            {"Hidden reasoning.</think>你好 🌿 café.", "你好 🌿 café."},
            {"Hidden reasoning.</think>Use the literal `</think>` in this example.", "Use the literal `</think>` in this example."},
            {"Hidden reasoning.</think>First line.\nSecond line.", "First line.\nSecond line."},
            {"Still thinking without an answer.", ""},
            {"Hidden reasoning.</thi", ""},
            {"Hidden reasoning.</think>", ""},
        };
        int checks = 0;
        for (const auto & test : cases) for (int mode = 0; mode < 3; ++mode) {
            auto input = signal_grove::local_chat_inputs();
            common_chat_msg user; user.role = "user"; user.content = "Write a short sentence about a peaceful garden";
            input.messages.push_back(user);
            common_chat_session session(templates.get(), vocab, input, {});
            signal_grove::FinalAnswerProjection projection; std::string displayed;
            auto feed = [&](const common_chat_input & chunk) { displayed += projection.append(session.feed(chunk)); };
            if (mode == 0) feed(common_chat_input_tokenize(vocab, test.first));
            else for (auto token : common_tokenize(vocab, test.first, false, true)) {
                const auto piece = common_token_to_piece(vocab, token, true);
                if (mode == 1) { common_chat_input chunk; chunk.append(piece, token); feed(chunk); }
                else for (char byte : piece) { common_chat_input chunk; chunk.append(std::string(1, byte), token); feed(chunk); }
            }
            displayed += projection.append(session.finish()); require(displayed == test.second); ++checks;
        }
        common_chat_msg msg; signal_grove::FinalAnswerProjection projection;
        msg.content = "Answer"; require(projection.append(msg) == "Answer");
        require(projection.append(msg).empty()); ++checks;
        for (const auto & bad : std::vector<std::string>{"An altered answer", "Ans", std::string(131073, 'x')}) {
            msg.content = bad; bool refused = false;
            try { projection.append(msg); } catch (const std::exception &) { refused = true; }
            require(refused); ++checks;
        }
        msg.content = "Answer"; msg.tool_calls.emplace_back(); bool refused = false;
        try { projection.append(msg); } catch (const std::exception &) { refused = true; }
        require(refused); ++checks;
        const std::vector<std::string> allowed = {"phone.one", "host.two", "worker.three", "phone.four", "phone.five"};
        const int expected[] = {1, 4, 15, 40, 85};
        for (int n = 1; n <= 5; ++n) {
            std::vector<std::string> ids(allowed.begin(), allowed.begin() + n);
            auto configured = signal_grove::local_chat_inputs(ids);
            auto alternatives = nlohmann::ordered_json::parse(configured.json_schema).at("enum");
            require(alternatives.size() == expected[n - 1]); std::set<std::string> variants;
            for (const auto & option : alternatives) {
                require(option.is_array() && option.size() >= 1 && option.size() <= 3);
                std::set<std::string> seen;
                for (const auto & id : option) {
                    auto value = id.get<std::string>(); require(std::find(ids.begin(), ids.end(), value) != ids.end());
                    require(seen.insert(value).second);
                }
                require(variants.insert(option.dump()).second);
            }
            ++checks;
        }
        for (const auto & bad : std::vector<std::vector<std::string>>{
            {"same", "same"}, {"a", "b", "c", "d", "e", "f"}, {"unsafe\"choice"}, {std::string(65, 'x')}, {""}}) {
            bool refused = false;
            try { signal_grove::local_chat_inputs(bad); } catch (const std::exception &) { refused = true; }
            require(refused); ++checks;
        }
        require(signal_grove::local_chat_inputs().json_schema.empty()); ++checks;
        auto input = signal_grove::local_chat_inputs(); common_chat_msg user; user.role = "user"; user.content = "Hello"; input.messages.push_back(user);
        common_chat_session session(templates.get(), vocab, input, {}); common_params_sampling sampling;
        for (int total : {1, 48, 96, 384, 512}) {
            signal_grove::apply_local_chat_sampling(session, vocab, sampling, total);
            require(sampling.reasoning_budget_tokens == std::min(64, total / 4));
            require(sampling.generation_prompt == session.generation_prompt());
            require(!sampling.reasoning_budget_start.empty() && !sampling.reasoning_budget_end.empty());
            require(sampling.reasoning_budget_forced == sampling.reasoning_budget_end.front()); ++checks;
        }
        signal_grove::apply_local_chat_sampling(session, vocab, sampling, 384);
        auto * budget = common_reasoning_budget_init(vocab, {sampling.reasoning_budget_start}, sampling.reasoning_budget_end,
            sampling.reasoning_budget_forced, sampling.reasoning_budget_tokens);
        for (auto token : common_tokenize(vocab, sampling.generation_prompt, false, true)) llama_sampler_accept(budget, token);
        require(common_reasoning_budget_get_state(budget) == REASONING_BUDGET_COUNTING); ++checks;
        auto plain = common_tokenize(vocab, "x", false, false); require(plain.size() == 1);
        for (int i = 0; i < 64; ++i) llama_sampler_accept(budget, plain.front());
        require(common_reasoning_budget_get_state(budget) == REASONING_BUDGET_FORCING); ++checks;
        for (auto forced : sampling.reasoning_budget_forced) {
            std::vector<llama_token_data> data; for (int id = 0; id < llama_vocab_n_tokens(vocab); ++id) data.push_back({id, 0.0f, 0.0f});
            llama_token_data_array current = {data.data(), data.size(), -1, false}; llama_sampler_apply(budget, &current);
            int finite = 0; for (const auto & entry : data) if (std::isfinite(entry.logit)) { ++finite; require(entry.id == forced); }
            require(finite == 1); llama_sampler_accept(budget, forced);
        }
        ++checks;
        require(common_reasoning_budget_get_state(budget) == REASONING_BUDGET_DONE);
        require(common_reasoning_budget_get_end_match(budget) && *common_reasoning_budget_get_end_match(budget) == sampling.reasoning_budget_forced); ++checks;
        llama_sampler_free(budget);
        std::cout << "NATIVE_CHAT_OUTPUT_PASS checks=" << checks << " vocab_only=true" << std::endl;
        templates.reset(); llama_model_free(model); llama_backend_free(); return 0;
    } catch (const std::exception &) { std::cerr << "NATIVE_CHAT_OUTPUT_FAILED" << std::endl; return 1; }
}
