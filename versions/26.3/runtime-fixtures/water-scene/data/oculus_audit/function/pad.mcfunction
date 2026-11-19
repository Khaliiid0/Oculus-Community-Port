# ONLY the disposable Nether/End origin: safe room for a stable observation.
execute unless dimension minecraft:overworld run fill -3 79 -3 3 79 3 minecraft:stone
execute unless dimension minecraft:overworld run fill -3 80 -3 3 84 3 minecraft:air
execute unless dimension minecraft:overworld run setblock 0 79 0 minecraft:sea_lantern
execute unless dimension minecraft:overworld run tp @s 0.5 80 0.5 0 10
