package com.openworld.racer.engine3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

object MeshFactory {

    class MeshData(
        val vertexBuffer: FloatBuffer,
        val indexBuffer: ShortBuffer,
        val indexCount: Int
    )

    val cubeMesh: MeshData by lazy { createCubeMesh() }
    val cylinderMesh: MeshData by lazy { createCylinderMesh(16) }
    val wedgeMesh: MeshData by lazy { createWedgeMesh() }
    val coneMesh: MeshData by lazy { createConeMesh(8) }
    val gemMesh: MeshData by lazy { createGemMesh() }

    private fun createCubeMesh(): MeshData {
        val vertices = floatArrayOf(
            // Front face (+Z, normal 0, 0, 1)
            -0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
             0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
             0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
            -0.5f,  0.5f,  0.5f,  0f, 0f, 1f,

            // Back face (-Z, normal 0, 0, -1)
            -0.5f, -0.5f, -0.5f,  0f, 0f, -1f,
            -0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f, -0.5f, -0.5f,  0f, 0f, -1f,

            // Top face (+Y, normal 0, 1, 0)
            -0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
            -0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
             0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
             0.5f,  0.5f, -0.5f,  0f, 1f, 0f,

            // Bottom face (-Y, normal 0, -1, 0)
            -0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
             0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
             0.5f, -0.5f,  0.5f,  0f, -1f, 0f,
            -0.5f, -0.5f,  0.5f,  0f, -1f, 0f,

            // Right face (+X, normal 1, 0, 0)
             0.5f, -0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f,  0.5f,  1f, 0f, 0f,
             0.5f, -0.5f,  0.5f,  1f, 0f, 0f,

            // Left face (-X, normal -1, 0, 0)
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f,
            -0.5f, -0.5f,  0.5f, -1f, 0f, 0f,
            -0.5f,  0.5f,  0.5f, -1f, 0f, 0f,
            -0.5f,  0.5f, -0.5f, -1f, 0f, 0f
        )

        val indices = shortArrayOf(
             0,  1,  2,   0,  2,  3,
             4,  5,  6,   4,  6,  7,
             8,  9, 10,   8, 10, 11,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 21, 22,  20, 22, 23
        )

        return buildMesh(vertices, indices)
    }

    private fun createCylinderMesh(segments: Int): MeshData {
        val vertsList = mutableListOf<Float>()
        val indicesList = mutableListOf<Short>()

        // 1. Side wall
        for (i in 0 until segments) {
            val a = (2.0 * Math.PI * i / segments).toFloat()
            val nx = cos(a)
            val nz = sin(a)

            // Bottom ring vertex
            vertsList.addAll(listOf(0.5f * nx, -0.5f, 0.5f * nz, nx, 0f, nz))
            // Top ring vertex
            vertsList.addAll(listOf(0.5f * nx,  0.5f, 0.5f * nz, nx, 0f, nz))
        }

        for (i in 0 until segments) {
            val n = (i + 1) % segments
            val b1 = (i * 2).toShort()
            val t1 = (i * 2 + 1).toShort()
            val b2 = (n * 2).toShort()
            val t2 = (n * 2 + 1).toShort()
            indicesList.addAll(listOf(b1, b2, t1, t1, b2, t2))
        }

        // 2. Top cap
        val topCenterIdx = (vertsList.size / 6).toShort()
        vertsList.addAll(listOf(0f, 0.5f, 0f, 0f, 1f, 0f))
        val topRingStart = (vertsList.size / 6).toShort()

        for (i in 0 until segments) {
            val a = (2.0 * Math.PI * i / segments).toFloat()
            vertsList.addAll(listOf(0.5f * cos(a), 0.5f, 0.5f * sin(a), 0f, 1f, 0f))
        }

        for (i in 0 until segments) {
            val n = (i + 1) % segments
            indicesList.addAll(listOf(topCenterIdx, (topRingStart + i).toShort(), (topRingStart + n).toShort()))
        }

        // 3. Bottom cap
        val botCenterIdx = (vertsList.size / 6).toShort()
        vertsList.addAll(listOf(0f, -0.5f, 0f, 0f, -1f, 0f))
        val botRingStart = (vertsList.size / 6).toShort()

        for (i in 0 until segments) {
            val a = (2.0 * Math.PI * i / segments).toFloat()
            vertsList.addAll(listOf(0.5f * cos(a), -0.5f, 0.5f * sin(a), 0f, -1f, 0f))
        }

        for (i in 0 until segments) {
            val n = (i + 1) % segments
            indicesList.addAll(listOf(botCenterIdx, (botRingStart + n).toShort(), (botRingStart + i).toShort()))
        }

        return buildMesh(vertsList.toFloatArray(), indicesList.toShortArray())
    }

    private fun createWedgeMesh(): MeshData {
        val vertices = floatArrayOf(
            // Sloped Top/Front Face (normal sloped 0, 0.7071, 0.7071)
            -0.5f, -0.5f,  0.5f,  0f, 0.7071f, 0.7071f,
             0.5f, -0.5f,  0.5f,  0f, 0.7071f, 0.7071f,
             0.5f,  0.5f, -0.5f,  0f, 0.7071f, 0.7071f,
            -0.5f,  0.5f, -0.5f,  0f, 0.7071f, 0.7071f,

            // Bottom Face (-Y, normal 0, -1, 0)
            -0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
             0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
             0.5f, -0.5f,  0.5f,  0f, -1f, 0f,
            -0.5f, -0.5f,  0.5f,  0f, -1f, 0f,

            // Back Face (-Z, normal 0, 0, -1)
            -0.5f, -0.5f, -0.5f,  0f, 0f, -1f,
            -0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f, -0.5f, -0.5f,  0f, 0f, -1f,

            // Right Face (+X, triangle, normal 1, 0, 0)
             0.5f, -0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f, -0.5f,  1f, 0f, 0f,
             0.5f, -0.5f,  0.5f,  1f, 0f, 0f,

            // Left Face (-X, triangle, normal -1, 0, 0)
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f,
            -0.5f, -0.5f,  0.5f, -1f, 0f, 0f,
            -0.5f,  0.5f, -0.5f, -1f, 0f, 0f
        )

        val indices = shortArrayOf(
             0,  1,  2,   0,  2,  3, // Sloped Top/Front
             4,  5,  6,   4,  6,  7, // Bottom
             8,  9, 10,   8, 10, 11, // Back
            12, 13, 14,              // Right
            15, 16, 17               // Left
        )

        return buildMesh(vertices, indices)
    }

    private fun createConeMesh(segments: Int): MeshData {
        val vertsList = mutableListOf<Float>()
        val indicesList = mutableListOf<Short>()

        // 1. Side triangles
        for (i in 0 until segments) {
            val a1 = (2.0 * Math.PI * i / segments).toFloat()
            val a2 = (2.0 * Math.PI * (i + 1) / segments).toFloat()
            val midA = (a1 + a2) / 2f
            val nx = cos(midA) * 0.894f
            val ny = 0.447f
            val nz = sin(midA) * 0.894f

            val idx = (vertsList.size / 6).toShort()
            vertsList.addAll(listOf(
                0f, 0.5f, 0f, nx, ny, nz,
                0.5f * cos(a1), -0.5f, 0.5f * sin(a1), nx, ny, nz,
                0.5f * cos(a2), -0.5f, 0.5f * sin(a2), nx, ny, nz
            ))
            indicesList.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort()))
        }

        // 2. Base cap
        val botCenter = (vertsList.size / 6).toShort()
        vertsList.addAll(listOf(0f, -0.5f, 0f, 0f, -1f, 0f))
        val baseStart = (vertsList.size / 6).toShort()

        for (i in 0 until segments) {
            val a = (2.0 * Math.PI * i / segments).toFloat()
            vertsList.addAll(listOf(0.5f * cos(a), -0.5f, 0.5f * sin(a), 0f, -1f, 0f))
        }

        for (i in 0 until segments) {
            val n = (i + 1) % segments
            indicesList.addAll(listOf(botCenter, (baseStart + n).toShort(), (baseStart + i).toShort()))
        }

        return buildMesh(vertsList.toFloatArray(), indicesList.toShortArray())
    }

    private fun createGemMesh(): MeshData {
        val s = 0.5f
        val vertsList = mutableListOf<Float>()
        val indicesList = mutableListOf<Short>()

        val top = floatArrayOf(0f, s, 0f)
        val bot = floatArrayOf(0f, -s, 0f)
        val eq = arrayOf(
            floatArrayOf(s, 0f, 0f),
            floatArrayOf(0f, 0f, s),
            floatArrayOf(-s, 0f, 0f),
            floatArrayOf(0f, 0f, -s)
        )

        // 4 Upper Triangles
        for (i in 0..3) {
            val p1 = eq[i]
            val p2 = eq[(i + 1) % 4]
            val nx = (p1[0] + p2[0]) / 2f
            val ny = 0.7071f
            val nz = (p1[2] + p2[2]) / 2f
            val idx = (vertsList.size / 6).toShort()
            vertsList.addAll(listOf(top[0], top[1], top[2], nx, ny, nz))
            vertsList.addAll(listOf(p1[0], p1[1], p1[2], nx, ny, nz))
            vertsList.addAll(listOf(p2[0], p2[1], p2[2], nx, ny, nz))
            indicesList.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort()))
        }

        // 4 Lower Triangles
        for (i in 0..3) {
            val p1 = eq[i]
            val p2 = eq[(i + 1) % 4]
            val nx = (p1[0] + p2[0]) / 2f
            val ny = -0.7071f
            val nz = (p1[2] + p2[2]) / 2f
            val idx = (vertsList.size / 6).toShort()
            vertsList.addAll(listOf(bot[0], bot[1], bot[2], nx, ny, nz))
            vertsList.addAll(listOf(p2[0], p2[1], p2[2], nx, ny, nz))
            vertsList.addAll(listOf(p1[0], p1[1], p1[2], nx, ny, nz))
            indicesList.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort()))
        }

        return buildMesh(vertsList.toFloatArray(), indicesList.toShortArray())
    }

    private fun buildMesh(vertices: FloatArray, indices: ShortArray): MeshData {
        val vBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vBuffer.position(0)

        val iBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
        iBuffer.position(0)

        return MeshData(vBuffer, iBuffer, indices.size)
    }
}
