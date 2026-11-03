// Android CPU allocation adapter for the pinned MIT llama.cpp-omni runtime.
// Expert tensors must be writable and must not alias the read-only GGUF mmap.
#pragma once
#include "ggml-backend-impl.h"
#include "ggml-cpu.h"
static const char * chevere_expert_buffer_name(ggml_backend_buffer_type_t) { return "CPU_Chevere_Experts"; }
static ggml_backend_buffer_type_t chevere_cpu_expert_buffer() {
    // A distinct buffer type selects ordinary allocation in llama-model.cpp.
    // It delegates allocation/alignment/host access to the CPU backend unchanged.
    // The loader keys contexts by buffer NAME, not pointer identity.
    static ggml_backend_buffer_type writable = [] {
        auto type = *ggml_backend_cpu_buffer_type();
        type.iface.get_name = chevere_expert_buffer_name;
        return type;
    }();
    return &writable;
}
