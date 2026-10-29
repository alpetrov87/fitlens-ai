package com.fitlens.ai.workouts

import java.util.UUID

data class ExerciseDefinition(val key: String, val name: String, val instructions: String)
object WorkoutTemplates {
    const val SAFETY = "Движи се контролирано, без задържане на дъха и прекомерно напъване. Спри при болка. Закрепи ластика надеждно и използвай стабилна опора. Това не е медицинска оценка за индивидуално състояние."
    val exercises = listOf(
        ExerciseDefinition("march", "March in Place", "Марширувай на място с умерено повдигане на коленете и спокойно дишане."),
        ExerciseDefinition("arm_circles", "Arm Circles", "Прави малки контролирани кръгове с ръце без болка в раменете."),
        ExerciseDefinition("hip_circles", "Hip Circles", "С ръце на таза прави бавни кръгове с малка амплитуда."),
        ExerciseDefinition("shallow_squat", "Shallow Squats", "Клекни плитко, с ходила на пода и колене по посока на пръстите."),
        ExerciseDefinition("squat", "Squat", "Седни назад с таза, дръж ходилата стабилни и се изправи плавно с издишване."),
        ExerciseDefinition("incline_pushup", "Incline Push-up", "Постави длани на стабилна повдигната опора. Сгъни лакти и избутай плавно с издишване."),
        ExerciseDefinition("band_row", "Resistance Band Row", "Закрепи ластика сигурно. Изтегли лакти назад, без люлеене и повдигане на раменете."),
        ExerciseDefinition("reverse_lunge", "Reverse Lunge", "Отстъпи назад и спусни таза в комфортен обхват. Редувай краката и използвай опора при нужда."),
        ExerciseDefinition("calf_raise", "Calf Raise", "С опора за равновесие повдигни пети, задръж кратко и спусни контролирано."),
        ExerciseDefinition("band_pull_apart", "Resistance Band Pull Apart", "Разтвори ластика пред гърди с леко свити лакти, без извиване на кръста."),
        ExerciseDefinition("sit_to_stand", "Sit to Stand", "Седни на стабилен стол, наклони се леко напред и се изправи плавно с издишване."),
        ExerciseDefinition("band_chest_press", "Resistance Band Chest Press", "Закрепи ластика стабилно зад теб и избутай ръце напред без задържане на дъха."),
        ExerciseDefinition("glute_bridge", "Glute Bridge", "Легни със свити колене. Повдигни таза плавно в комфортен обхват, без прекомерно стягане на корема."),
        ExerciseDefinition("standing_hip_abduction", "Standing Hip Abduction", "Дръж стабилна опора и отведи крака встрани с малка амплитуда, без накланяне на тялото."),
    )
    fun step(key: String, kind: StepKind = StepKind.EXERCISE, seconds: Int = 40, rest: Int = 20): WorkoutStep {
        val e = exercises.first { it.key == key }
        return WorkoutStep(kind = kind, name = e.name, exerciseKey = key, instructions = e.instructions, value = seconds, restSeconds = rest)
    }
    fun all(): List<Workout> = listOf(
        template("A", "Вторник", "Ластик; стабилна повдигната опора за лицеви опори", listOf("squat", "incline_pushup", "band_row", "reverse_lunge", "calf_raise", "band_pull_apart")),
        template("B", "Четвъртък", "Ластик; стабилен стол или опора", listOf("sit_to_stand", "band_chest_press", "band_row", "glute_bridge", "standing_hip_abduction", "calf_raise")),
    )
    private fun template(letter: String, day: String, equipment: String, main: List<String>): Workout {
        val id = UUID.nameUUIDFromBytes("bair-strength-$letter-v1".toByteArray()).toString()
        val warmup = listOf("march", "arm_circles", "hip_circles", "shallow_squat").map { step(it, StepKind.WARMUP, 30, 0) }
        return Workout(id = id, name = "Bair Strength $letter", description = "$day · 14 минути. $SAFETY", equipment = equipment,
            steps = warmup + WorkoutStep(kind = StepKind.REPEAT, name = "Основна част", iterations = 2, children = main.map { step(it) }),
            isTemplate = true, createdAt = 1_700_000_000_000L)
    }
}
