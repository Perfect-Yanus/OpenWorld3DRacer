package com.openworld.racer.model

enum class ObjectType {
    ROAD,
    SIDEWALK,
    BUILDING,
    SIGNBOARD,
    RIVER,
    LAKE,
    TREE,
    LAMPPOST,
    SPEEDBUMP,
    GRASS
}

data class EnvironmentObject(
    val id: String,
    val type: ObjectType,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float,
    val rotationY: Float = 0f,
    val color: Int = 0xFF888888.toInt(),
    val isCollidable: Boolean = true
) {
    // Check AABB 3D Collision with Car Bounding Box
    fun intersectsBoundingBox(carX: Float, carY: Float, carZ: Float, carRadius: Float): Boolean {
        if (!isCollidable) return false
        val halfX = sizeX / 2f
        val halfZ = sizeZ / 2f

        val minX = posX - halfX - carRadius
        val maxX = posX + halfX + carRadius
        val minZ = posZ - halfZ - carRadius
        val maxZ = posZ + halfZ + carRadius

        return (carX in minX..maxX) && (carZ in minZ..maxZ) && (carY <= posY + sizeY)
    }
}
