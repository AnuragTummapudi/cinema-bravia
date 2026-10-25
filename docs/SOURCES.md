# Live source allowlist

Canonical configuration: `cinema-launcher/app/src/main/assets/trailer-sources.json`. It contains channel IDs, upload playlist IDs, category/language policies and ownership-reference URLs. Runtime videos are discovered from those channels, not committed as a list.

The allowlist was resolved against primary YouTube channel metadata and publisher identity links during development. Trust IDs, not matching names or inferred verification badges. A future ownership change needs review.

- **Telugu:** 16 production-house/music-label channels. Telugu-only and explicit-Telugu policies differ so dubbed/ambiguous multi-language uploads can be filtered conservatively.
- **Hollywood:** Universal Pictures, 20th Century Studios, Paramount Pictures, Sony Pictures Entertainment, Lionsgate Movies and Warner Bros.
- **Technology:** OpenAI, Google DeepMind, Google, Anthropic, Claude, Y Combinator, Tesla, Figure, Boston Dynamics, NVIDIA, Apple, Microsoft, SpaceX and Unitree Robotics. YC admits recent official discussion/design/founder videos as well as launches; the other channels use stricter launch/demo evidence.

The profile/reference links are evidence pointers, not a guarantee about every future upload. Regional metadata, provider availability and topic names can change. Review additions and representative titles with the policy fixtures; never admit a similarly named personal channel merely because its handle looks plausible.

There is no API key in this configuration. Private watch history, subscriptions and signed-in account data are not queried. Public metadata caches and research HTML dumps are deliberately excluded from the repository.
