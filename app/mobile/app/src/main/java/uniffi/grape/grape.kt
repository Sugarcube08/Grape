package uniffi.grape

data class SleepReport(
    val score: Double,
    val need: Double,
    val debt: Double,
    val efficiency: Double,
    val latency: Double,
    val disturbances: Int,
    val remMinutes: Double,
    val deepMinutes: Double,
    val lightMinutes: Double,
    val awakeMinutes: Double
)

data class RecoveryReport(
    val recoveryScore: Double,
    val recoveryState: String,
    val hrv: Double,
    val restingHr: Double,
    val temperatureDelta: Double
)

fun computeSleep(databasePath: String): String = GrapeJni.computeSleep(databasePath)
fun computeRecovery(databasePath: String): String = GrapeJni.computeRecovery(databasePath)
fun computeStrain(databasePath: String): String = GrapeJni.computeStrain(databasePath)
fun computeStress(databasePath: String): String = GrapeJni.computeStress(databasePath)
fun insertPacket(databasePath: String, frameHex: String, deviceType: String): String = 
    GrapeJni.insertPacket(databasePath, frameHex, deviceType)
fun getDeviceState(databasePath: String): String = GrapeJni.getDeviceState(databasePath)

fun computeSleepV1(databasePath: String): SleepReport? {
    val jsonStr = GrapeJni.jniComputeSleepV1(databasePath) ?: return null
    return try {
        val obj = org.json.JSONObject(jsonStr)
        SleepReport(
            score = obj.getDouble("score"),
            need = obj.getDouble("need"),
            debt = obj.getDouble("debt"),
            efficiency = obj.getDouble("efficiency"),
            latency = obj.getDouble("latency"),
            disturbances = obj.getInt("disturbances"),
            remMinutes = obj.getDouble("rem_minutes"),
            deepMinutes = obj.getDouble("deep_minutes"),
            lightMinutes = obj.getDouble("light_minutes"),
            awakeMinutes = obj.getDouble("awake_minutes")
        )
    } catch (e: Exception) {
        null
    }
}

fun sleepSummary(databasePath: String): String = GrapeJni.sleepSummary(databasePath)
fun sleepStageDistribution(databasePath: String): String = GrapeJni.sleepStageDistribution(databasePath)
fun sleepEfficiency(databasePath: String): Double = GrapeJni.sleepEfficiency(databasePath)
fun sleepDebt(databasePath: String): Double = GrapeJni.sleepDebt(databasePath)

fun computeRecoveryV0(databasePath: String): RecoveryReport? {
    val jsonStr = GrapeJni.jniComputeRecoveryV0(databasePath) ?: return null
    return try {
        val obj = org.json.JSONObject(jsonStr)
        RecoveryReport(
            recoveryScore = obj.getDouble("recovery_score"),
            recoveryState = obj.getString("recovery_state"),
            hrv = obj.getDouble("hrv"),
            restingHr = obj.getDouble("resting_hr"),
            temperatureDelta = obj.getDouble("temperature_delta")
        )
    } catch (e: Exception) {
        null
    }
}

fun buildCommandFrame(sequence: UByte, command: UByte, data: List<UByte>): List<UByte> {
    val byteArray = GrapeJni.buildCommandFrame(sequence.toByte(), command.toByte(), data.map { it.toByte() }.toByteArray())
    return byteArray.map { it.toUByte() }
}

object GrapeJni {
    init {
        System.loadLibrary("grape_core")
    }

    external fun computeSleep(databasePath: String): String
    external fun computeRecovery(databasePath: String): String
    external fun computeStrain(databasePath: String): String
    external fun computeStress(databasePath: String): String
    external fun insertPacket(databasePath: String, frameHex: String, deviceType: String): String
    external fun getDeviceState(databasePath: String): String
    
    external fun jniComputeSleepV1(databasePath: String): String?
    external fun sleepSummary(databasePath: String): String
    external fun sleepStageDistribution(databasePath: String): String
    external fun sleepEfficiency(databasePath: String): Double
    external fun sleepDebt(databasePath: String): Double
    
    external fun jniComputeRecoveryV0(databasePath: String): String?
    
    external fun buildCommandFrame(sequence: Byte, command: Byte, data: ByteArray): ByteArray
}
