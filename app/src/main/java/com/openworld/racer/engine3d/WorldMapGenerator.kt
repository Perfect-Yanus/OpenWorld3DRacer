package com.openworld.racer.engine3d

import com.openworld.racer.model.CollectibleItem
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ItemType
import com.openworld.racer.model.ObjectType

object WorldMapGenerator {

    fun generateOpenWorldObjects(): List<EnvironmentObject> {
        val objects = mutableListOf<EnvironmentObject>()
        var idCounter = 1

        // 1. Vast Asphalt Ground Base Plane
        objects.add(
            EnvironmentObject(
                id = "ground_base",
                type = ObjectType.ROAD,
                posX = 0f, posY = -0.05f, posZ = 0f,
                sizeX = 1200f, sizeY = 0.1f, sizeZ = 1200f,
                color = 0xFF12141C.toInt(), // Deep Dark Asphalt
                isCollidable = false
            )
        )

        // 2. High-Speed Main Stunt Boulevard (North-South, X = 0)
        val mainRoadWidth = 16.0f
        objects.add(
            EnvironmentObject(
                id = "road_main_ns",
                type = ObjectType.ROAD,
                posX = 0f, posY = 0.002f, posZ = 0f,
                sizeX = mainRoadWidth, sizeY = 0.02f, sizeZ = 700f,
                color = 0xFF1A1C24.toInt(),
                isCollidable = false
            )
        )

        // Center Road Markings
        for (z in -340..340 step 16) {
            objects.add(
                EnvironmentObject(
                    id = "line_main_$z",
                    type = ObjectType.ROAD_LINE,
                    posX = 0f, posY = 0.015f, posZ = z.toFloat(),
                    sizeX = 0.40f, sizeY = 0.025f, sizeZ = 8.0f,
                    color = 0xFFFFD600.toInt(),
                    isCollidable = false
                )
            )
        }

        // Road Neon Curbs / Rumble Strips (Red & White Checkered)
        for (z in -340..340 step 12) {
            val isRed = (z / 12) % 2 == 0
            val curbColor = if (isRed) 0xFFFF1744.toInt() else 0xFFECEFF1.toInt()
            // Left Curb
            objects.add(
                EnvironmentObject(
                    id = "curb_l_$z",
                    type = ObjectType.RUMBLE_STRIP,
                    posX = -mainRoadWidth / 2f - 0.4f, posY = 0.04f, posZ = z.toFloat(),
                    sizeX = 0.9f, sizeY = 0.08f, sizeZ = 11.5f,
                    color = curbColor,
                    isCollidable = false
                )
            )
            // Right Curb
            objects.add(
                EnvironmentObject(
                    id = "curb_r_$z",
                    type = ObjectType.RUMBLE_STRIP,
                    posX = mainRoadWidth / 2f + 0.4f, posY = 0.04f, posZ = z.toFloat(),
                    sizeX = 0.9f, sizeY = 0.08f, sizeZ = 11.5f,
                    color = curbColor,
                    isCollidable = false
                )
            )
        }

        // 3. Stunt Jump Ramp 1: The Plaza Mega Launch (X = 0, Z = 60)
        // Slopes up towards +Z, launching cars into the air!
        objects.add(
            EnvironmentObject(
                id = "ramp_plaza_1",
                type = ObjectType.JUMP_RAMP,
                posX = 0f, posY = 0.0f, posZ = 60f,
                sizeX = 14.5f, sizeY = 3.2f, sizeZ = 12.0f,
                rotationY = 180f, // Slope up towards +Z
                color = 0xFFFFEA00.toInt(), // Glowing Stunt Yellow
                isCollidable = true
            )
        )

        // Speed Boost Pad leading right up to Ramp 1
        objects.add(
            EnvironmentObject(
                id = "boost_ramp_1",
                type = ObjectType.BOOST_PAD,
                posX = 0f, posY = 0.02f, posZ = 35f,
                sizeX = 11.0f, sizeY = 0.03f, sizeZ = 14.0f,
                color = 0xFF00FF88.toInt(), // Neon Electric Green
                isCollidable = false
            )
        )

        // 4. Stunt Jump Ramp 2: The River Chasm Mega Leap (X = 0, Z = -110)
        // Scenic River Chasm across Z = -130 to -170
        val riverZ = -150f
        objects.add(
            EnvironmentObject(
                id = "river_chasm",
                type = ObjectType.RIVER,
                posX = 0f, posY = -0.4f, posZ = riverZ,
                sizeX = 800f, sizeY = 0.5f, sizeZ = 45f,
                color = 0xFF0288D1.toInt(),
                isCollidable = true
            )
        )

        // Takeoff Ramp (slopes up towards -Z over the river!)
        objects.add(
            EnvironmentObject(
                id = "ramp_chasm_takeoff",
                type = ObjectType.JUMP_RAMP,
                posX = 0f, posY = 0.0f, posZ = -120f,
                sizeX = 14.5f, sizeY = 3.8f, sizeZ = 14.0f,
                rotationY = 0f, // Slope up towards -Z
                color = 0xFFFF5722.toInt(), // Blaze Orange
                isCollidable = true
            )
        )

        // Landing Ramp across the river!
        objects.add(
            EnvironmentObject(
                id = "ramp_chasm_landing",
                type = ObjectType.JUMP_RAMP,
                posX = 0f, posY = 0.0f, posZ = -180f,
                sizeX = 15.0f, sizeY = 3.6f, sizeZ = 14.0f,
                rotationY = 180f,
                color = 0xFF00E5FF.toInt(), // Cyan Landing
                isCollidable = true
            )
        )

        // Speed Boost Pad right before Chasm Takeoff!
        objects.add(
            EnvironmentObject(
                id = "boost_chasm",
                type = ObjectType.BOOST_PAD,
                posX = 0f, posY = 0.02f, posZ = -95f,
                sizeX = 11.0f, sizeY = 0.03f, sizeZ = 14.0f,
                color = 0xFF00FF88.toInt(),
                isCollidable = false
            )
        )

        // 5. Stunt Jump Ramp 3: The South Sky Leap (X = 0, Z = 220)
        objects.add(
            EnvironmentObject(
                id = "ramp_south_3",
                type = ObjectType.JUMP_RAMP,
                posX = 0f, posY = 0.0f, posZ = 220f,
                sizeX = 14.5f, sizeY = 3.4f, sizeZ = 12.0f,
                rotationY = 180f,
                color = 0xFFE040FB.toInt(), // Neon Purple
                isCollidable = true
            )
        )

        objects.add(
            EnvironmentObject(
                id = "boost_south_3",
                type = ObjectType.BOOST_PAD,
                posX = 0f, posY = 0.02f, posZ = 195f,
                sizeX = 11.0f, sizeY = 0.03f, sizeZ = 14.0f,
                color = 0xFF00FF88.toInt(),
                isCollidable = false
            )
        )

        // 6. East Highway Loop (X = 120) & West Drift Loop (X = -120)
        for (xSign in intArrayOf(-1, 1)) {
            val loopX = xSign * 120.0f
            // Parallel Highway
            objects.add(
                EnvironmentObject(
                    id = "road_parallel_$xSign",
                    type = ObjectType.ROAD,
                    posX = loopX, posY = 0.002f, posZ = 0f,
                    sizeX = 14.0f, sizeY = 0.02f, sizeZ = 600f,
                    color = 0xFF1A1C24.toInt(),
                    isCollidable = false
                )
            )

            // Connecting Highway Ramps / Overpasses
            objects.add(
                EnvironmentObject(
                    id = "road_connector_north_$xSign",
                    type = ObjectType.ROAD,
                    posX = loopX / 2f, posY = 0.002f, posZ = -280f,
                    sizeX = 120f, sizeY = 0.02f, sizeZ = 14.0f,
                    color = 0xFF1A1C24.toInt(),
                    isCollidable = false
                )
            )

            objects.add(
                EnvironmentObject(
                    id = "road_connector_south_$xSign",
                    type = ObjectType.ROAD,
                    posX = loopX / 2f, posY = 0.002f, posZ = 280f,
                    sizeX = 120f, sizeY = 0.02f, sizeZ = 14.0f,
                    color = 0xFF1A1C24.toInt(),
                    isCollidable = false
                )
            )

            // Side Jump Ramps on Parallel Highway
            objects.add(
                EnvironmentObject(
                    id = "ramp_parallel_$xSign",
                    type = ObjectType.JUMP_RAMP,
                    posX = loopX, posY = 0f, posZ = 40f,
                    sizeX = 12.0f, sizeY = 2.8f, sizeZ = 10.0f,
                    rotationY = if (xSign > 0) 180f else 0f,
                    color = 0xFFFFD600.toInt(),
                    isCollidable = true
                )
            )

            objects.add(
                EnvironmentObject(
                    id = "boost_parallel_$xSign",
                    type = ObjectType.BOOST_PAD,
                    posX = loopX, posY = 0.02f, posZ = if (xSign > 0) 15f else 65f,
                    sizeX = 10.0f, sizeY = 0.03f, sizeZ = 12.0f,
                    color = 0xFF00FF88.toInt(),
                    isCollidable = false
                )
            )
        }

        // 7. Neon Overhead Arch Gantries (Finish / Speed Trap Gates)
        val archPositions = floatArrayOf(-240f, -50f, 130f, 290f)
        for ((idx, az) in archPositions.withIndex()) {
            objects.add(
                EnvironmentObject(
                    id = "arch_gate_$idx",
                    type = ObjectType.ARCH_GATE,
                    posX = 0f, posY = 6.0f, posZ = az,
                    sizeX = 18.0f, sizeY = 1.2f, sizeZ = 1.6f,
                    color = 0xFF00E5FF.toInt(), // Glowing Neon Cyan Arch
                    isCollidable = false
                )
            )
            // Left Pillar
            objects.add(
                EnvironmentObject(
                    id = "arch_pillar_l_$idx",
                    type = ObjectType.LAMPPOST,
                    posX = -9.2f, posY = 3.0f, posZ = az,
                    sizeX = 0.8f, sizeY = 6.0f, sizeZ = 0.8f,
                    color = 0xFF37474F.toInt(),
                    isCollidable = true
                )
            )
            // Right Pillar
            objects.add(
                EnvironmentObject(
                    id = "arch_pillar_r_$idx",
                    type = ObjectType.LAMPPOST,
                    posX = 9.2f, posY = 3.0f, posZ = az,
                    sizeX = 0.8f, sizeY = 6.0f, sizeZ = 0.8f,
                    color = 0xFF37474F.toInt(),
                    isCollidable = true
                )
            )
        }

        // 8. Sleek Cyberpunk Skyscrapers & Tower Architecture
        val towerColors = intArrayOf(
            0xFF1E293B.toInt(), 0xFF0F172A.toInt(), 0xFF1E1B4B.toInt(),
            0xFF022C22.toInt(), 0xFF31103F.toInt(), 0xFF172554.toInt()
        )
        val neonAccentColors = intArrayOf(
            0xFF00E5FF.toInt(), 0xFFFF007F.toInt(), 0xFFFFD600.toInt(), 0xFF00FF88.toInt()
        )

        val cityBlocks = arrayOf(
            Pair(-60f, -80f), Pair(60f, -80f),
            Pair(-60f, 120f), Pair(60f, 120f),
            Pair(-190f, 0f), Pair(190f, 0f),
            Pair(-190f, -180f), Pair(190f, -180f),
            Pair(-190f, 180f), Pair(190f, 180f)
        )

        for ((bIdx, block) in cityBlocks.withIndex()) {
            val bx = block.first
            val bz = block.second

            val height = 45.0f + (bIdx * 11) % 55f
            val width = 36.0f
            val depth = 36.0f
            val baseColor = towerColors[bIdx % towerColors.size]
            val neonColor = neonAccentColors[bIdx % neonAccentColors.size]

            // Main Tower Body
            objects.add(
                EnvironmentObject(
                    id = "tower_main_$bIdx",
                    type = ObjectType.BUILDING,
                    posX = bx, posY = height / 2f, posZ = bz,
                    sizeX = width, sizeY = height, sizeZ = depth,
                    color = baseColor,
                    isCollidable = true
                )
            )

            // Illuminated Neon Window Ring Accent
            objects.add(
                EnvironmentObject(
                    id = "tower_neon_$bIdx",
                    type = ObjectType.SIGNBOARD,
                    posX = bx, posY = height * 0.72f, posZ = bz,
                    sizeX = width * 1.02f, sizeY = 1.4f, sizeZ = depth * 1.02f,
                    color = neonColor,
                    isCollidable = false
                )
            )

            // Spire Crown on Rooftop
            objects.add(
                EnvironmentObject(
                    id = "tower_crown_$bIdx",
                    type = ObjectType.SIGNBOARD,
                    posX = bx, posY = height + 4.0f, posZ = bz,
                    sizeX = width * 0.4f, sizeY = 8.0f, sizeZ = depth * 0.4f,
                    color = 0xFF0A0F1D.toInt(),
                    isCollidable = false
                )
            )
        }

        // 9. Street Palm Trees & Cyber Lampposts along Central Highway
        for (z in -320..320 step 40) {
            if (z in -170..-130) continue // Skip river area

            // Trees
            objects.add(
                EnvironmentObject(
                    id = "tree_left_$z",
                    type = ObjectType.TREE,
                    posX = -mainRoadWidth / 2f - 3.5f, posY = 3.5f, posZ = z.toFloat(),
                    sizeX = 2.4f, sizeY = 7.0f, sizeZ = 2.4f,
                    color = 0xFF10B981.toInt(),
                    isCollidable = true
                )
            )
            objects.add(
                EnvironmentObject(
                    id = "tree_right_$z",
                    type = ObjectType.TREE,
                    posX = mainRoadWidth / 2f + 3.5f, posY = 3.5f, posZ = z.toFloat(),
                    sizeX = 2.4f, sizeY = 7.0f, sizeZ = 2.4f,
                    color = 0xFF10B981.toInt(),
                    isCollidable = true
                )
            )

            // Lampposts every 80m
            if (z % 80 == 0) {
                objects.add(
                    EnvironmentObject(
                        id = "lamp_left_$z",
                        type = ObjectType.LAMPPOST,
                        posX = -mainRoadWidth / 2f - 1.8f, posY = 3.8f, posZ = z.toFloat(),
                        sizeX = 0.5f, sizeY = 7.6f, sizeZ = 0.5f,
                        color = 0xFF64748B.toInt(),
                        isCollidable = false
                    )
                )
                objects.add(
                    EnvironmentObject(
                        id = "lamp_right_$z",
                        type = ObjectType.LAMPPOST,
                        posX = mainRoadWidth / 2f + 1.8f, posY = 3.8f, posZ = z.toFloat(),
                        sizeX = 0.5f, sizeY = 7.6f, sizeZ = 0.5f,
                        color = 0xFF64748B.toInt(),
                        isCollidable = false
                    )
                )
            }
        }

        return objects
    }

    fun generateCollectibleItems(): List<CollectibleItem> {
        val items = mutableListOf<CollectibleItem>()
        var idCounter = 1

        // 1. Airtime Gold Gems Arc over Stunt Jump Ramp 1 (X = 0, Z = 60 to 110)
        // Mid-air arc rewarding players who launch off Ramp 1!
        val ramp1Zs = floatArrayOf(75f, 85f, 95f, 105f, 115f)
        val ramp1Ys = floatArrayOf(4.5f, 6.8f, 7.5f, 6.4f, 4.0f)
        for (i in ramp1Zs.indices) {
            items.add(
                CollectibleItem(
                    id = "gem_air_ramp1_${idCounter++}",
                    type = ItemType.GOLD_GEM,
                    posX = 0f,
                    posY = ramp1Ys[i],
                    posZ = ramp1Zs[i]
                )
            )
        }

        // 2. High-Altitude Mega Star over the River Chasm Jump (X = 0, Z = -150, Y = 9.0)
        items.add(
            CollectibleItem(
                id = "star_chasm_apex_${idCounter++}",
                type = ItemType.MEGA_STAR,
                posX = 0f,
                posY = 9.0f,
                posZ = -150f
            )
        )
        // Flanking Gold Gems across the chasm leap
        items.add(
            CollectibleItem(
                id = "gem_chasm_1_${idCounter++}",
                type = ItemType.GOLD_GEM,
                posX = 0f,
                posY = 6.5f,
                posZ = -135f
            )
        )
        items.add(
            CollectibleItem(
                id = "gem_chasm_2_${idCounter++}",
                type = ItemType.GOLD_GEM,
                posX = 0f,
                posY = 6.5f,
                posZ = -165f
            )
        )

        // 3. Airtime Gold Gems Arc over South Ramp 3 (X = 0, Z = 235 to 270)
        val ramp3Zs = floatArrayOf(235f, 245f, 255f, 265f)
        val ramp3Ys = floatArrayOf(4.8f, 7.0f, 6.8f, 4.2f)
        for (i in ramp3Zs.indices) {
            items.add(
                CollectibleItem(
                    id = "gem_air_ramp3_${idCounter++}",
                    type = ItemType.GOLD_GEM,
                    posX = 0f,
                    posY = ramp3Ys[i],
                    posZ = ramp3Zs[i]
                )
            )
        }

        // 4. Nitro Boost Fuel Canisters along Straightaways
        val nitroZs = floatArrayOf(-220f, -80f, 15f, 170f, 310f)
        for (z in nitroZs) {
            // Left lane & Right lane
            items.add(
                CollectibleItem(
                    id = "nitro_item_l_${idCounter++}",
                    type = ItemType.NITRO_BOOST,
                    posX = -3.8f,
                    posY = 1.0f,
                    posZ = z
                )
            )
            items.add(
                CollectibleItem(
                    id = "nitro_item_r_${idCounter++}",
                    type = ItemType.NITRO_BOOST,
                    posX = 3.8f,
                    posY = 1.0f,
                    posZ = z
                )
            )
        }

        // 5. Roadside Gold Gem Lines along Straightaways
        for (z in -260..260 step 25) {
            if (z in -180..-100) continue
            items.add(
                CollectibleItem(
                    id = "gem_road_center_${idCounter++}",
                    type = ItemType.GOLD_GEM,
                    posX = if ((z / 25) % 2 == 0) -2.5f else 2.5f,
                    posY = 0.9f,
                    posZ = z.toFloat()
                )
            )
        }

        // 6. Collectibles on East & West Parallel Highways
        for (xSign in intArrayOf(-1, 1)) {
            val loopX = xSign * 120.0f
            // Mega Star on parallel highway jump
            items.add(
                CollectibleItem(
                    id = "star_parallel_$xSign",
                    type = ItemType.MEGA_STAR,
                    posX = loopX,
                    posY = 6.5f,
                    posZ = if (xSign > 0) 55f else 25f
                )
            )

            // Nitro Canisters
            items.add(
                CollectibleItem(
                    id = "nitro_parallel_1_$xSign",
                    type = ItemType.NITRO_BOOST,
                    posX = loopX,
                    posY = 1.0f,
                    posZ = -100f
                )
            )
            items.add(
                CollectibleItem(
                    id = "nitro_parallel_2_$xSign",
                    type = ItemType.NITRO_BOOST,
                    posX = loopX,
                    posY = 1.0f,
                    posZ = 120f
                )
            )

            // Corner Curve Gem Arcs
            for (cz in -220..220 step 30) {
                items.add(
                    CollectibleItem(
                        id = "gem_parallel_${xSign}_$cz",
                        type = ItemType.GOLD_GEM,
                        posX = loopX + (if ((cz / 30) % 2 == 0) -2.0f else 2.0f),
                        posY = 0.9f,
                        posZ = cz.toFloat()
                    )
                )
            }
        }

        return items
    }
}
