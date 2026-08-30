package com.openworld.racer.engine3d

import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ObjectType

object WorldMapGenerator {

    fun generateOpenWorldObjects(): List<EnvironmentObject> {
        val objects = mutableListOf<EnvironmentObject>()
        var idCounter = 1

        // Map dimensions: 800m x 800m
        // 1. Asphalt Ground Plane
        objects.add(
            EnvironmentObject(
                id = "ground_${idCounter++}",
                type = ObjectType.ROAD,
                posX = 0f, posY = -0.05f, posZ = 0f,
                sizeX = 1000f, sizeY = 0.1f, sizeZ = 1000f,
                color = 0xFF2B2B2B.toInt(),
                isCollidable = false
            )
        )

        // 2. Main City Grid Roads (Avenues)
        val roadWidth = 14.0f
        val blockSpacing = 120.0f

        for (x in -3..3) {
            val roadX = x * blockSpacing
            objects.add(
                EnvironmentObject(
                    id = "road_v_$x",
                    type = ObjectType.ROAD,
                    posX = roadX, posY = 0.0f, posZ = 0.0f,
                    sizeX = roadWidth, sizeY = 0.02f, sizeZ = 800.0f,
                    color = 0xFF1C1C1C.toInt(),
                    isCollidable = false
                )
            )
        }

        for (z in -3..3) {
            val roadZ = z * blockSpacing
            objects.add(
                EnvironmentObject(
                    id = "road_h_$z",
                    type = ObjectType.ROAD,
                    posX = 0.0f, posY = 0.0f, posZ = roadZ,
                    sizeX = 800.0f, sizeY = 0.02f, sizeZ = roadWidth,
                    color = 0xFF1C1C1C.toInt(),
                    isCollidable = false
                )
            )
        }

        // 3. Buildings & Storefronts with Billboards (간판)
        val buildingColors = intArrayOf(
            0xFF3949AB.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(),
            0xFF5E35B1.toInt(), 0xFFF4511E.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt()
        )
        val billboardColors = intArrayOf(
            0xFFFFEA00.toInt(), 0xFF00E5FF.toInt(), 0xFFFF1744.toInt(), 0xFF00E676.toInt()
        )

        for (bx in -3..2) {
            for (bz in -3..2) {
                // Center coordinates of city block
                val cx = bx * blockSpacing + blockSpacing / 2f
                val cz = bz * blockSpacing + blockSpacing / 2f

                // Avoid lake/river district (Eastern side, x > 150)
                if (cx > 150f && cz > 0f) continue

                // 4 Skyscrapers per block
                val offsets = arrayOf(
                    Pair(-25f, -25f), Pair(25f, -25f),
                    Pair(-25f, 25f), Pair(25f, 25f)
                )

                for ((idx, offset) in offsets.withIndex()) {
                    val bX = cx + offset.first
                    val bZ = cz + offset.second

                    val bHeight = 25f + ((idx + bx + bz) * 7.5f % 45f)
                    val bWidth = 32f
                    val bDepth = 32f
                    val color = buildingColors[(bx * 3 + bz + idx).coerceIn(0, buildingColors.size - 1) % buildingColors.size]

                    // Building Box
                    objects.add(
                        EnvironmentObject(
                            id = "bldg_${bx}_${bz}_$idx",
                            type = ObjectType.BUILDING,
                            posX = bX, posY = bHeight / 2f, posZ = bZ,
                            sizeX = bWidth, sizeY = bHeight, sizeZ = bDepth,
                            color = color,
                            isCollidable = true
                        )
                    )

                    // Illuminated Billboard Sign (간판) on front facade
                    val signColor = billboardColors[(idx + bx) % billboardColors.size]
                    objects.add(
                        EnvironmentObject(
                            id = "sign_${bx}_${bz}_$idx",
                            type = ObjectType.SIGNBOARD,
                            posX = bX, posY = 6.0f, posZ = bZ + bDepth / 2f + 0.5f,
                            sizeX = 14f, sizeY = 4f, sizeZ = 0.8f,
                            color = signColor,
                            isCollidable = false
                        )
                    )
                }

                // Sidewalk around block (인도)
                objects.add(
                    EnvironmentObject(
                        id = "sidewalk_${bx}_$bz",
                        type = ObjectType.SIDEWALK,
                        posX = cx, posY = 0.12f, posZ = cz,
                        sizeX = 90f, sizeY = 0.24f, sizeZ = 90f,
                        color = 0xFF757575.toInt(),
                        isCollidable = true
                    )
                )
            }
        }

        // 4. Street Trees (가로수) along Avenues
        for (x in -3..3) {
            val treeX = x * blockSpacing + 8.5f
            for (z in -350..350 step 35) {
                // Avoid river/lake area
                if (treeX > 150f && z > 0) continue

                objects.add(
                    EnvironmentObject(
                        id = "tree_${x}_$z",
                        type = ObjectType.TREE,
                        posX = treeX, posY = 3.5f, posZ = z.toFloat(),
                        sizeX = 2.5f, sizeY = 7.0f, sizeZ = 2.5f,
                        color = 0xFF2E7D32.toInt(),
                        isCollidable = true
                    )
                )
            }
        }

        // 5. Scenic River (강) & Lake (호수) District
        // River flowing from North to South
        val riverX = 220.0f
        objects.add(
            EnvironmentObject(
                id = "river_main",
                type = ObjectType.RIVER,
                posX = riverX, posY = -0.3f, posZ = -100f,
                sizeX = 50f, sizeY = 0.4f, sizeZ = 500f,
                color = 0xFF0288D1.toInt(),
                isCollidable = true
            )
        )

        // Large Lake (호수) in North East
        objects.add(
            EnvironmentObject(
                id = "lake_main",
                type = ObjectType.LAKE,
                posX = 280.0f, posY = -0.3f, posZ = 220f,
                sizeX = 220f, sizeY = 0.4f, sizeZ = 220f,
                color = 0xFF01579B.toInt(),
                isCollidable = true
            )
        )

        // Bridge over River
        objects.add(
            EnvironmentObject(
                id = "bridge_river",
                type = ObjectType.ROAD,
                posX = riverX, posY = 0.1f, posZ = 0.0f,
                sizeX = 65f, sizeY = 0.3f, sizeZ = 16f,
                color = 0xFF424242.toInt(),
                isCollidable = false
            )
        )

        // 6. Speed Bumps (스피드 범프) on Main Roads
        objects.add(
            EnvironmentObject(
                id = "bump_1",
                type = ObjectType.SPEEDBUMP,
                posX = 0f, posY = 0.08f, posZ = 50f,
                sizeX = 14f, sizeY = 0.16f, sizeZ = 2.0f,
                color = 0xFFFFEA00.toInt(),
                isCollidable = true
            )
        )

        objects.add(
            EnvironmentObject(
                id = "bump_2",
                type = ObjectType.SPEEDBUMP,
                posX = 0f, posY = 0.08f, posZ = -80f,
                sizeX = 14f, sizeY = 0.16f, sizeZ = 2.0f,
                color = 0xFFFFEA00.toInt(),
                isCollidable = true
            )
        )

        return objects
    }
}
