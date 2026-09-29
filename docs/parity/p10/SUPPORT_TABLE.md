# P10 disc support table

| Source / feature | P10 route | Interactive menus | Notes |
|---|---|---:|---|
| VIDEO_TS directory | Native SageTV MiniDVDPlayer / P07-P09 | Yes | Authorized SageTV MediaFile only. |
| Parent/mounted directory containing VIDEO_TS | Native SageTV MiniDVDPlayer / P07-P09 | Yes | VIDEO_TS child is identified explicitly. |
| Physical DVD drive | Stock SageTV DVD VM | Yes | Server owns drive access/mounting. |
| ISO | Stock-server ISO mount prerequisite, then native DVD | Yes after successful server mount | Plugin never elevates or mounts. |
| Explicit DVD title, movie-only | FFmpeg `dvdvideo` compatibility plan | No | Requires runtime dvdvideo + libdvdread + libdvdnav and explicit title. |
| Individual VOB | Ordinary file | No | Not promoted to DVD navigation. |
| Imported MKV | Ordinary file | No | Disc-derived origin does not make it a DVD VM source. |
| Blu-ray / BDMV / BD-J | Unsupported/pending scope | No claim | Separate from proven DVD work. |
| Encrypted disc / secure DRM | Unsupported/pending scope | No claim | No decryption/DRM bypass is implemented or claimed. |
