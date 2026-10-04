package com.openworld.racer

import com.openworld.racer.engine3d.WorldMapGenerator
import com.openworld.racer.model.CollectibleItem
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ItemType
import com.openworld.racer.model.ObjectType
import com.openworld.racer.model.VehicleConfig
import com.openworld.racer.physics.RaycastVehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhysicsAndGameplayTest {

    private lateinit var vehicle: RaycastVehicle
    private lateinit var config: VehicleConfig

    @Before
    fun setUp() {
        config = VehicleConfig(
            motorLayout = "AWD",
            maxPowerKw = 450f,
            maxTorqueNm = 750f,
            batteryCapacityKwh = 85f
        )
        vehicle = RaycastVehicle(config)
    }

    @Test
    fun testNoFlyingCarBug_AltitudeClampedAndFinite() {
        vehicle.resetPosition(0f, 0f, 0f, 0f)
        val objects = WorldMapGenerator.generateOpenWorldObjects()

        // Simulate 200 high-throttle physics steps
        vehicle.throttleInput = 1.0f
        for (i in 0..200) {
            vehicle.update(0.016f, objects)
            // posY must never shoot into outer space
            assertTrue("posY must be >= 0, was ${vehicle.posY}", vehicle.posY >= -1.0f)
            assertTrue("posY must never exceed 32m ceiling, was ${vehicle.posY}", vehicle.posY <= 32.0f)
            assertFalse("posY must not be NaN", vehicle.posY.isNaN())
            assertFalse("posX must not be NaN", vehicle.posX.isNaN())
            assertFalse("posZ must not be NaN", vehicle.posZ.isNaN())
        }
    }

    @Test
    fun testJumpRampLaunchAndLanding() {
        vehicle.resetPosition(0f, 0f, 40f, 180f) // Facing +Z towards Ramp 1 at Z = 60
        vehicle.velZ = 20f // ~72 km/h forward speed

        val objects = listOf(
            EnvironmentObject(
                id = "test_ramp",
                type = ObjectType.JUMP_RAMP,
                posX = 0f, posY = 0f, posZ = 60f,
                sizeX = 14f, sizeY = 3.0f, sizeZ = 12f,
                rotationY = 180f,
                isCollidable = true
            )
        )

        var launchedAirborne = false
        var landedSuccessfully = false
        val initialScore = vehicle.totalScore

        // Step physics forward across ramp and through air (3.6s total duration)
        for (step in 0..180) {
            vehicle.update(0.02f, objects)
            if (vehicle.isAirborne) {
                launchedAirborne = true
                assertTrue("Airborne height should be positive", vehicle.posY > 0.1f)
            } else if (launchedAirborne && vehicle.posY <= 0.05f) {
                landedSuccessfully = true
            }
        }

        assertTrue("Vehicle must launch into the air off the ramp", launchedAirborne)
        assertTrue("Vehicle must land back safely on ground level", landedSuccessfully)
        assertTrue("Vehicle must earn stunt points upon landing", vehicle.totalScore > initialScore)
    }

    @Test
    fun testCollectibleItemsPickupAndCombo() {
        vehicle.resetPosition(0f, 0f, 0f, 0f)
        val items = listOf(
            CollectibleItem("gem_1", ItemType.GOLD_GEM, 0f, 0f, 5f),
            CollectibleItem("nitro_1", ItemType.NITRO_BOOST, 0f, 0f, 15f)
        )

        val initialScore = vehicle.totalScore
        val initialNitro = vehicle.nitroGauge

        // Move car forward over gem_1
        vehicle.posZ = 5f
        vehicle.update(0.016f, emptyList(), items)

        assertTrue("Gold gem must be collected", items[0].isCollected)
        assertTrue("Score must increase", vehicle.totalScore > initialScore)
        assertTrue("Combo multiplier must increase", vehicle.comboMultiplier > 1)

        // Move car forward over nitro_1
        vehicle.posZ = 15f
        vehicle.update(0.016f, emptyList(), items)

        assertTrue("Nitro item must be collected", items[1].isCollected)
        assertTrue("Nitro gauge must increase", vehicle.nitroGauge > initialNitro)
        assertTrue("Nitro burst must activate", vehicle.isNitroActive)
    }

    @Test
    fun testBoostPadAcceleration() {
        vehicle.resetPosition(0f, 0f, 10f, 180f) // Heading +Z
        vehicle.velZ = 5f // Moving forward ~18 km/h
        val initialSpeed = vehicle.speedKmh

        val boostPads = listOf(
            EnvironmentObject(
                id = "pad_1",
                type = ObjectType.BOOST_PAD,
                posX = 0f, posY = 0.02f, posZ = 10f,
                sizeX = 10f, sizeY = 0.03f, sizeZ = 10f,
                isCollidable = false
            )
        )

        vehicle.update(0.016f, boostPads)

        assertTrue("Speed must increase substantially after boost pad", vehicle.speedKmh > initialSpeed + 30f)
        assertEquals("Surface should be BOOST", "BOOST", vehicle.currentSurface)
    }

    @Test
    fun testSmoothProgressiveSteering() {
        vehicle.resetPosition(0f, 0f, 0f, 0f)
        vehicle.steeringInput = 1.0f

        // Initial smoothedSteer should start at 0
        assertEquals(0.0f, vehicle.smoothedSteer, 0.001f)

        // After small dt, steer smoothly ramps up without instant binary jump
        vehicle.update(0.016f, emptyList())
        assertTrue("Steer should smoothly ramp up (> 0)", vehicle.smoothedSteer > 0.05f)
        assertTrue("Steer should not instantly snap to 1.0", vehicle.smoothedSteer < 1.0f)
    }
}
