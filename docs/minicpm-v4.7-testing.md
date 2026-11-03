# MiniCPM-V 4.7 experiment

Chevere supports the pinned **35B-A3B LES CPU bundle** as a separate `LLAMA_CPP` engine. This is an experiment for local model testing, not a prediction of iPhone speed or a claim of production readiness. Existing LiteRT models remain available.

## Build

Initialize native source once with `git submodule update --init --depth 1`. The submodule pins `tc-mb/llama.cpp-omni` at `e0eca9d8f57ac8f5a5ad6191a8007b93436c1e71`, the revision used by OpenBMB/MiniCPM-V-Apps. Builds use NDK 27.0.12077973 and CMake 3.22.1, arm64-v8a and x86_64. Native kernels use optimization even in a debug app. Run the normal assembleDebug task locally; no model weights are bundled in the APK and no CI GPU/emulator jobs are added.

CMake makes build-local copies of two pinned source files. The model adapter supplies a distinct writable CPU buffer type for stripped expert tensors (the upstream implementation only provides Metal). The MoE adapter marks the Apple-only F_NOCACHE hint unsupported on Android. It does not modify the submodule, routing or weights. Android keeps normal file caching; this is not equivalent to Apple's cache behavior. The backend is linked statically with hidden archive symbols to avoid collisions with LiteRT. The runtime MIT license, image decoder notice, model Apache-2.0 license text and model/conversion provenance ship in assets/third_party/; native/transitive/model licensing still needs the release review.

## Download and run

Select MiniCPM-V 4.7 35B-A3B in Models. Read the experimental description and use Download Engine. The app-owned manifest pins the model revision `7a7ab2ac8f045d53574ead3324b97e709d0e318b`, all 43 relative paths, sizes and SHA-256 checksums. It includes the stripped language model, vision projector, expert manifest and all 40 expert layers. About 16.6 GB downloads to internal app storage. Reserve at least 17 GB plus free space for the app/OS; 12 GB RAM and fast internal storage are recommended. Loading refuses less than 3 GiB available RAM but this is not an OOM guarantee.

The small WorkManager request references the built-in manifest; it does not serialize 43 URLs/hashes into WorkManager's 10 KB data limit. Network retries retain partial files and revalidate each file before installation. Explicit cancellation discards the partial bundle. A complete verified bundle is atomically renamed into place; registry health scans require every expected file size. Deleting the inactive model removes its directory through the existing model lifecycle.

Text and one attached image are supported. Images are decoded to a bounded 1344-pixel longest edge and use one overview slice. No audio, video, DSpark speculative decoding, GPU/NPU acceleration or generic GGUF import is enabled. Context is 4096 tokens with up to 1024 output tokens; the native tokenizer checks the full formatted request and image chunks before inference, rejecting requests that do not fit. Every request clears native memory; the app supplies bounded history. Thought text stays hidden. Native inference is serialized; cancellation sets only an atomic abort flag and waits for the worker before releasing the handle. Model-load cancellation also unloads partial state.

Token badges use runtime input/output counts. Background composer predictions currently use the LiteRT-specific isolated session and are unavailable with MiniCPM. Chat, document synthesis and routed agent inference use InferenceManager and therefore the selected engine. Tools are still subject to the same confirmation and category rules.

## Validation

Run MiniCpmBundleTest and InferenceManagerLifecycleTest, plus the full debug unit suite and lint. `.maestro/minicpm_catalog_validation.yaml` checks catalog descriptions and runtime identity without downloading 16 GB. `.maestro/minicpm_inference_validation.yaml` requires a fully downloaded bundle; it activates the model and checks a short text response. `MiniCpmNativeSmokeTest` runs on a downloaded bundle and checks exact context rejection, cancellation/reuse, arithmetic text and a solid-red image; it skips if the bundle is absent. Vision, cancellation during prefill, cross-engine switching, thermals and sustained benchmarks require device evidence; never infer them from a catalog-only check.

Sources: https://github.com/OpenBMB/MiniCPM-V-Apps and https://huggingface.co/tc-mb/MiniCPM-V-Apps-gguf .

## Recorded device evidence (November 2, 2026)

The Red Magic NX709J completed the in-app 43-file download and SHA-256 verification. Native smoke coverage passed text, solid-red image recognition, exact context rejection and cancellation/reuse between prefill and decode (194.084 seconds total). The normal chat flow activated MiniCPM and returned `4` in a fresh chat, showing 339 measured input tokens and one output token in 45.91 seconds; native prefill consumed 45.455 seconds. Activation took about 2.073 seconds. These are individual observations, not a sustained throughput benchmark. This CPU path is substantially slower to prefill than the mobile-optimized LiteRT models tested earlier; no iPhone performance claim applies. See minicpm-v4.7-evidence.json.

The switch check confirms Gemma readiness and return to MiniCPM readiness; it handles Models restoring its details screen. The temporary test screenshot was removed and the device stay-awake setting restored to its original value. MiniCPM and its downloaded bundle remain installed and selected for user testing.
