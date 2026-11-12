#include "local_chat_output.h"
#include "common.h"
#include "ggml-backend.h"
#include "llama.h"
#include "log.h"
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
        std::cout << "NATIVE_CHAT_OUTPUT_PASS checks=" << checks << " vocab_only=true" << std::endl;
        templates.reset(); llama_model_free(model); llama_backend_free(); return 0;
    } catch (const std::exception &) { std::cerr << "NATIVE_CHAT_OUTPUT_FAILED" << std::endl; return 1; }
}
