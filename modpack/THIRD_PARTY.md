# Modpack third-party policy

The modpack builder references third-party files through Modrinth metadata instead of committing or re-uploading their jars.

Selected projects:
- Xaero's Minimap / Xaero's World Map — included as launcher references; credit retained. Their project pages explicitly permit modpack use subject to their stated conditions.
- Mouse Tweaks — BSD-3-Clause.
- Sound Physics Remastered — GPL-3.0-only.
- AmbientSounds — LGPL-3.0-only.
- AppleSkin — Unlicense.
- Jade — CC-BY-NC-SA-4.0.
- Clumps — MIT.
- Cloth Config API — LGPL-3.0-only.

LAST DAYS source/assets continue to carry the separate third-party notices already present in the repository.

The workflow does not bypass project distribution controls: it consumes the download URL and hashes returned by the Modrinth version API and writes those references into the .mrpack index.
