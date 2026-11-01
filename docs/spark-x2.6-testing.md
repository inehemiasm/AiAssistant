# Spark X2.6 on Android

The experimental **Spark X2.6 4B (INT8 CPU)** catalog entry uses LiteRT-LM
0.19.0. The download is 4,238,081,840 bytes and requires additional free RAM
and storage to load. It is text-only. Thinking is disabled, the context limit
is 4,096 tokens, and each response is capped at 1,536 output tokens.

Open the model marketplace, download the entry, and select it after download.
Try three successive chat turns, then a document question and a confirmed tool
request. Check that replies remain visible, stop/cancel works, and internal
thought text never appears as a reply or tool request. Switch back to an existing
model to check that it still loads and responds.

The curated file is pinned to revision
`f8da72659679424c286a526b77a39fb5f669dd72` and checked against SHA-256
`3fae1eca71a67a03e597240e802c3ea17574a9f22499e38cfa9a7d76cebb7565`.
The upstream conversion reports CPU validation; Chevere-specific device
inference still needs validation. INT4 and KV2048 variants are not curated:
INT4 has reported CPU multi-turn failures and KV2048 needs different token and
reasoning settings. Do not rename these variants to the supported INT8 filename.

Upstream: https://huggingface.co/litert-community/Spark-X2.6-4B
