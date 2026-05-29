package com.neo.chevere.data.chat

/**
 * Phrase sets used by [ChatRequestRouter] for lightweight local prompt routing.
 *
 * These are intentionally kept in Kotlin instead of Android string resources because they are
 * classifier inputs, not UI copy. Keeping them here makes routing deterministic and easy to test.
 */
internal object ChatRoutingLexicon {
    val imageRequestVerbs = setOf("create", "generate", "make", "draw", "render", "paint")
    val imageRequestNouns = setOf("image", "picture", "photo", "art", "illustration", "portrait")
    val capabilityQuestionPrefixes = listOf(
        "can you",
        "could you",
        "are you able to",
        "do you know how to",
        "do you have the ability to"
    )
    val concreteImageDescriptionMarkers = listOf(
        " of ",
        " showing ",
        " with ",
        " in ",
        " at ",
        " wearing ",
        " holding "
    )

    val sensorCapabilityTerms = setOf(
        "sensor", "sensors", "device sensors", "hardware sensors", "ambient light",
        "pressure", "barometer", "battery", "thermals", "temperature", "gyroscope",
        "accelerometer", "compass", "proximity", "sound level", "noise level",
        "metal", "magnet", "magnetic field", "stud", "studs", "level", "flatness"
    )

    val sensorScreenTerms = setOf(
        "sensor dashboard", "sensor radar", "radar", "stud finder", "metal detector",
        "spirit level", "bubble level", "light meter", "proximity detector"
    )

    val sensorReadingActionTerms = setOf(
        "read", "check", "measure", "tell me", "what is", "what's", "how hot",
        "how cold", "how warm", "how bright", "how dark", "how loud", "how noisy",
        "how quiet", "which way", "where is", "am i", "is it", "is there"
    )

    val sensorKeywords = setOf(
        // English
        "sensor", "sensors", "barometer", "pressure", "lux", "brightness",
        "battery", "thermal", "thermals", "cpu temp", "device temp", "internal temp",
        "room temp", "ambient temp", "temperature",
        "gyro", "gyroscope", "accelerometer", "magnetometer", "compass", "azimuth", "heading",
        "proximity", "flatness", "spirit level", "bubble level", "metal detector", "stud finder",
        "magnetic", "decibel", "sound level", "noise level",

        // Spanish
        "sensores", "presión", "presion", "brillo", "batería", "bateria", "térmico",
        "termico", "temperatura", "giroscopio", "acelerómetro", "acelerometro",
        "magnetómetro", "magnetometro", "brújula", "brujula", "proximidad",
        "nivel de burbuja", "detector de metales", "campo magnético", "campo magnetico",

        // Portuguese
        "pressão", "pressao", "giroscópio", "acelerômetro", "bússola", "bussola",
        "nível de bolha", "nivel de bolha", "detector de metal",

        // French
        "capteur", "capteurs", "pression", "luminosité", "luminosite", "batterie",
        "thermique", "température", "gyroscope", "accéléromètre", "accelerometre",
        "boussole", "proximité", "proximite", "niveau à bulle", "detecteur de metaux",
        "détecteur de métaux",

        // German
        "sensoren", "druck", "helligkeit", "akku", "thermisch", "temperatur",
        "gyroskop", "kompass", "näherung", "naherung", "näherungssensor",
        "naherungssensor", "wasserwaage", "metalldetektor"
    )

    val ambientPhrases = setOf(
        // English
        "how hot", "how cold", "how warm", "how bright", "is it hot", "is it cold",
        "room temperature", "ambient temperature", "device temperature", "internal temperature",
        "room condition", "room conditions", "hows the light", "how is the light", "light level",
        "light levels", "ambient light", "how noisy", "how loud", "how quiet", "noise level",
        "sound level", "sound in here", "noise in here", "am i moving", "is the phone still",
        "is it shaking", "which way is north", "facing east", "facing north", "facing west",
        "facing south", "phone in my pocket", "in my pocket", "is it face down", "is it covered",
        "is this table flat", "surface level", "is it flat", "is it level", "is the floor level",
        "metal nearby", "magnets nearby",

        // Spanish
        "qué calor", "que calor", "qué frío", "que frio", "temperatura ambiente",
        "temperatura del cuarto", "temperatura de la habitación", "temperatura de la habitacion",
        "temperatura del dispositivo", "temperatura interna", "nivel de luz", "niveles de luz",
        "luz ambiental", "cómo está la luz", "como esta la luz", "qué tan ruidoso",
        "que tan ruidoso", "cuánto ruido", "cuanto ruido", "nivel de ruido",
        "me estoy moviendo", "estoy moviéndome", "está quieto", "esta quieto",
        "está vibrando", "esta vibrando", "dónde está el norte", "donde esta el norte",
        "hacia dónde", "hacia donde", "rumbo al norte", "teléfono en mi bolsillo",
        "telefono en mi bolsillo", "en el bolsillo", "está boca abajo", "esta boca abajo",
        "mesa nivelada", "superficie plana", "está plano", "esta plano", "metal cerca",
        "imán cerca", "iman cerca",

        // Portuguese
        "temperatura do quarto", "temperatura do dispositivo", "temperatura interna",
        "nível de luz", "nivel de luz", "luz ambiente", "como está a luz", "como esta a luz",
        "quão barulhento", "quao barulhento", "quanto barulho", "nível de ruído",
        "nivel de ruido", "estou me movendo", "está parado", "esta parado", "onde fica o norte",
        "rumo ao norte", "celular no meu bolso", "no bolso", "virado para baixo",
        "mesa plana", "superfície plana", "superficie plana", "metal perto", "ímã perto",
        "ima perto",

        // French
        "fait chaud", "fait froid", "fait-il chaud", "fait-il froid", "fait il chaud",
        "fait il froid", "température ambiante", "temperature ambiante",
        "température de la pièce", "temperature de la piece", "température de la chambre",
        "temperature de la chambre", "température de l'appareil", "temperature de l'appareil",
        "niveau de lumière", "niveau de lumiere", "lumière ambiante", "lumiere ambiante",
        "comment est la lumière", "comment est la lumiere", "quel bruit", "c'est bruyant",
        "niveau de bruit", "est-ce que je bouge", "est-il immobile", "où est le nord",
        "ou est le nord", "quelle direction", "dans ma poche", "face contre terre",
        "table plate", "surface plane", "est-ce droit", "métal à proximité",
        "metal a proximite", "aimant à proximité", "aimant a proximite",

        // German
        "wie warm", "wie kalt", "wie heiss", "wie heiß", "raumtemperatur",
        "umgebungstemperatur", "gerätetemperatur", "geratetemperatur", "innentemperatur",
        "lichtstärke", "lichtstarke", "lichtverhältnisse", "lichtverhaltnisse",
        "wie ist das licht", "raumbeleuchtung", "wie laut", "wie leise", "lärmpegel",
        "larmpegel", "geräuschpegel", "gerauschpegel", "bewege ich mich", "bewegt sich",
        "ist es stabil", "wo ist norden", "himmelsrichtung", "in meiner tasche",
        "liegt auf dem display", "tisch gerade", "oberfläche eben", "oberflache eben",
        "ist es eben", "metall in der nähe", "metall in der nahe", "magnet in der nähe",
        "magnet in der nahe",

        // Japanese
        "音量はどれくらい", "うるさい", "静か", "騒音", "動いていますか", "歩いていますか",
        "揺れて", "北はどっち", "方角", "方位", "ポケットに入っていますか", "うつ伏せ",
        "テーブルは水平", "平らですか", "まっすぐ", "金属", "磁石"
    )

    val taskKeywords = setOf("task", "tasks", "todo", "todos", "to-do", "to-dos", "checklist")
    val taskActionVerbs = setOf(
        "create", "add", "new", "list", "show", "get", "view",
        "update", "change", "complete", "finish", "done",
        "delete", "remove", "clear", "mark"
    )

    val liveInformationWeatherTerms = setOf("weather", "forecast", "temperature")
    val freshInformationPhrases = setOf(
        "search the web",
        "look up",
        "latest",
        "current news",
        "today's news",
        "right now",
        "breaking news",
        "current price",
        "stock price"
    )

    val deviceActionVerbs = setOf(
        "copy",
        "share",
        "open",
        "launch",
        "draft",
        "email",
        "map",
        "navigate",
        "calendar"
    )
    val deviceActionTargets =
        setOf("clipboard", "share sheet", "browser", "url", "app", "maps", "email", "calendar")

    val modelManagementPhrases = setOf(
        "installed model",
        "installed models",
        "active model",
        "runtime status",
        "switch model",
        "select model"
    )
    val modelManagementActionVerbs =
        setOf("list", "show", "what", "which", "switch", "select", "recommend", "status")
}
