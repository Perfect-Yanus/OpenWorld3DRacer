package com.openworld.racer.engine3d

import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ObjectType

object WorldMapGenerator {

    fun generateOpenWorldObjects(): List<EnvironmentObject> {
        val objects = mutableListOf<EnvironmentObject>()
        var idCounter = 1

        // 1. Main Asphalt Ground Plane
        objects.add(
            EnvironmentObject(
                id = "ground_${idCounter++}",
                type = ObjectType.ROAD,
                posX = 0f, posY = -0.05f, posZ = 0f,
                sizeX = 1000f, sizeY = 0.1f, sizeZ = 1000f,
                color = 0xFF1B1B1E.toInt(),
                isCollidable = false
            )
        )

        // 2. City Grid Roads (Avenues) & Markings
        val roadWidth = 14.0f
        val blockSpacing = 120.0f
        val numBlocks = 3

        // Vertical Avenues (N-S)
        for (x in -numBlocks..numBlocks) {
            val roadX = x * blockSpacing
            objects.add(
                EnvironmentObject(
                    id = "road_v_$x",
                    type = ObjectType.ROAD,
                    posX = roadX, posY = 0.001f, posZ = 0.0f,
                    sizeX = roadWidth, sizeY = 0.02f, sizeZ = 800.0f,
                    color = 0xFF232328.toInt(),
                    isCollidable = false
                )
            )

            // Center Double Yellow Line
            for (z in -380..380 step 16) {
                objects.add(
                    EnvironmentObject(
                        id = "line_v_center_${x}_$z",
                        type = ObjectType.ROAD_LINE,
                        posX = roadX, posY = 0.015f, posZ = z.toFloat(),
                        sizeX = 0.35f, sizeY = 0.025f, sizeZ = 8.0f,
                        color = 0xFFFFD600.toInt(),
                        isCollidable = false
                    )
                )
            }
        }

        // Horizontal Avenues (E-W)
        for (z in -numBlocks..numBlocks) {
            val roadZ = z * blockSpacing
            objects.add(
                EnvironmentObject(
                    id = "road_h_$z",
                    type = ObjectType.ROAD,
                    posX = 0.0f, posY = 0.001f, posZ = roadZ,
                    sizeX = 800.0f, sizeY = 0.02f, sizeZ = roadWidth,
                    color = 0xFF232328.toInt(),
                    isCollidable = false
                )
            )

            // Center Double Yellow Line
            for (x in -380..380 step 16) {
                objects.add(
                    EnvironmentObject(
                        id = "line_h_center_${z}_$x",
                        type = ObjectType.ROAD_LINE,
                        posX = x.toFloat(), posY = 0.015f, posZ = roadZ,
                        sizeX = 8.0f, sizeY = 0.025f, sizeZ = 0.35f,
                        color = 0xFFFFD600.toInt(),
                        isCollidable = false
                    )
                )
            }
        }

        // Intersections Zebra Crosswalks
        for (x in -numBlocks..numBlocks) {
            for (z in -numBlocks..numBlocks) {
                val ix = x * blockSpacing
                val iz = z * blockSpacing

                // 4 Crosswalks per intersection
                val offsets = arrayOf(
                    Pair(0f, -roadWidth / 2f - 2f),
                    Pair(0f, roadWidth / 2f + 2f),
                    Pair(-roadWidth / 2f - 2f, 0f),
                    Pair(roadWidth / 2f + 2f, 0f)
                )

                for ((idx, off) in offsets.withIndex()) {
                    val isHorizontal = idx >= 2
                    val sx = if (isHorizontal) 3.5f else 11.0f
                    val sz = if (isHorizontal) 11.0f else 3.5f

                    objects.add(
                        EnvironmentObject(
                            id = "crosswalk_${x}_${z}_$idx",
                            type = ObjectType.CROSSWALK,
                            posX = ix + off.first, posY = 0.02f, posZ = iz + off.second,
                            sizeX = sx, sizeY = 0.025f, sizeZ = sz,
                            color = 0xFFECEFF1.toInt(),
                            isCollidable = false
                        )
                    )
                }
            }
        }

        // 3. Multi-Tiered Architectural Skyscrapers & Storefronts
        val buildingColors = intArrayOf(
            0xFF283593.toInt(), 0xFF00695C.toInt(), 0xFFC2185B.toInt(),
            0xFF4527A0.toInt(), 0xFFD84315.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt()
        )
        val billboardColors = intArrayOf(
            0xFFFFEA00.toInt(), 0xFF00E5FF.toInt(), 0xFFFF1744.toInt(), 0xFF00E676.toInt()
        )

        for (bx in -numBlocks until numBlocks) {
            for (bz in -numBlocks until numBlocks) {
                val cx = bx * blockSpacing + blockSpacing / 2f
                val cz = bz * blockSpacing + blockSpacing / 2f

                // Skip River / Lake District (East side, cx > 150)
                if (cx > 150f && cz > 0f) continue

                // Sidewalk Platform (인도)
                objects.add(
                    EnvironmentObject(
                        id = "sidewalk_${bx}_$bz",
                        type = ObjectType.SIDEWALK,
                        posX = cx, posY = 0.10f, posZ = cz,
                        sizeX = 94f, sizeY = 0.20f, sizeZ = 94f,
                        color = 0xFF607D8B.toInt(),
                        isCollidable = true
                    )
                )

                // 4 Skyscrapers per block
                val offsets = arrayOf(
                    Pair(-25f, -25f), Pair(25f, -25f),
                    Pair(-25f, 25f), Pair(25f, 25f)
                )

                for ((idx, offset) in offsets.withIndex()) {
                    val bX = cx + offset.first
                    val bZ = cz + offset.second

                    val baseHeight = 30f + Math.floorMod(idx + bx * 3 + bz * 7, 45).toFloat()
                    val bWidth = 34f
                    val bDepth = 34f
                    val colorIdx = Math.floorMod(bx * 3 + bz + idx, buildingColors.size)
                    val color = buildingColors[colorIdx]

                    // Tier 1: Base Podium
                    objects.add(
                        EnvironmentObject(
                            id = "bldg_base_${bx}_${bz}_$idx",
                            type = ObjectType.BUILDING,
                            posX = bX, posY = baseHeight * 0.2f, posZ = bZ,
                            sizeX = bWidth, sizeY = baseHeight * 0.4f, sizeZ = bDepth,
                            color = 0xFF37474F.toInt(),
                            isCollidable = true
                        )
                    )

                    // Tier 2: Main Tower
                    objects.add(
                        EnvironmentObject(
                            id = "bldg_mid_${bx}_${bz}_$idx",
                            type = ObjectType.BUILDING,
                            posX = bX, posY = baseHeight * 0.6f, posZ = bZ,
                            sizeX = bWidth * 0.82f, sizeY = baseHeight * 0.6f, sizeZ = bDepth * 0.82f,
                            color = color,
                            isCollidable = true
                        )
                    )

                    // Tier 3: Roof Spire / Top Crown
                    objects.add(
                        EnvironmentObject(
                            id = "bldg_top_${bx}_${bz}_$idx",
                            type = ObjectType.BUILDING,
                            posX = bX, posY = baseHeight + 3f, posZ = bZ,
                            sizeX = bWidth * 0.45f, sizeY = 6.0f, sizeZ = bDepth * 0.45f,
                            color = 0xFF263238.toInt(),
                            isCollidable = false
                        )
                    )

                    // Illuminated Billboard Sign (간판)
                    val signColorIdx = Math.floorMod(idx + bx, billboardColors.size)
                    val signColor = billboardColors[signColorIdx]
                    objects.add(
                        EnvironmentObject(
                            id = "sign_${bx}_${bz}_$idx",
                            type = ObjectType.SIGNBOARD,
                            posX = bX, posY = 7.5f, posZ = bZ + bDepth / 2f + 0.6f,
                            sizeX = 16f, sizeY = 4.5f, sizeZ = 0.8f,
                            color = signColor,
                            isCollidable = false
                        )
                    )
                }
            }
        }

        // 4. Street Trees (가로수) & Lampposts (가로등)
        for (x in -numBlocks..numBlocks) {
            val roadX = x * blockSpacing
            for (z in -350..350 step 35) {
                if (roadX > 150f && z > 0) continue

                // Trees along West side of road
                objects.add(
                    EnvironmentObject(
                        id = "tree_w_${x}_$z",
                        type = ObjectType.TREE,
                        posX = roadX - 9.5f, posY = 3.5f, posZ = z.toFloat(),
                        sizeX = 3.0f, sizeY = 7.5f, sizeZ = 3.0f,
                        color = 0xFF2E7D32.toInt(),
                        isCollidable = true
                    )
                )

                // Lampposts along East side of road
                if (z % 70 == 0) {
                    objects.add(
                        EnvironmentObject(
                            id = "lamp_e_${x}_$z",
                            type = ObjectType.LAMPPOST,
                            posX = roadX + 8.5f, posY = 3.8f, posZ = z.toFloat(),
                            sizeX = 0.6f, sizeY = 7.6f, sizeZ = 0.6f,
                            color = 0xFFB0BEC5.toInt(),
                            isCollidable = false
                        )
                    )
                }
            }
        }

        // 5. Scenic River (강), Lake (호수) & Bridge
        val riverX = 220.0f
        objects.add(
            EnvironmentObject(
                id = "river_main",
                type = ObjectType.RIVER,
                posX = riverX, posY = -0.3f, posZ = -100f,
                sizeX = 55f, sizeY = 0.4f, sizeZ = 500f,
                color = 0xFF0288D1.toInt(),
                isCollidable = true
            )
        )

        // Large Lake (호수)
        objects.add(
            EnvironmentObject(
                id = "lake_main",
                type = ObjectType.LAKE,
                posX = 280.0f, posY = -0.3f, posZ = 220f,
                sizeX = 230f, sizeY = 0.4f, sizeZ = 230f,
                color = 0xFF01579B.toInt(),
                isCollidable = true
            )
        )

        // Concrete Bridge over River
        objects.add(
            EnvironmentObject(
                id = "bridge_river",
                type = ObjectType.ROAD,
                posX = riverX, posY = 0.15f, posZ = 0.0f,
                sizeX = 70f, sizeY = 0.35f, sizeZ = 16f,
                color = 0xFF37474F.toInt(),
                isCollidable = false
            )
        )

        // 6. Speed Bumps (스피드 범프)
        objects.add(
            EnvironmentObject(
                id = "bump_1",
                type = ObjectType.SPEEDBUMP,
                posX = 0f, posY = 0.08f, posZ = 50f,
                sizeX = 14f, sizeY = 0.18f, sizeZ = 2.2f,
                color = 0xFFFFEA00.toInt(),
                isCollidable = true
            )
        )

        objects.add(
            EnvironmentObject(
                id = "bump_2",
                type = ObjectType.SPEEDBUMP,
                posX = 0f, posY = 0.08f, posZ = -80f,
                sizeX = 14f, sizeY = 0.18f, sizeZ = 2.2f,
                color = 0xFFFFEA00.toInt(),
                isCollidable = true
            )
        )

        return objects
    }
}
