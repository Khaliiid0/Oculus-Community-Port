# Direct world-buffer reads

26.2 development fixture for the internal terrain bridge. Uses its known colortex7
specular sentinel and a cleared RGB8_SNORM colortex8, reads both directly, then
overwrites logical colortex7 through a post stage. The next stage must read the post
result for 7 while still reading the unchanged world attachment for 8. Original world
storage must remain intact across frames. A normal scene passes; magenta indicates a
probe failure. This tests routing and storage, not pack-native terrain shading.
