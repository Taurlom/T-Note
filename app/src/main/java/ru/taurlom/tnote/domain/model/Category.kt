package ru.taurlom.tnote.domain.model

data class Category(val id: Long = 0, val name: String, val color: Long, val position: Int = 0) {

    companion object {

        /**
         * Цвет новой категории без явного выбора. Это доменное правило
         * («чем помечается свежий список»), а не свойство палитры UI:
         * домен не должен узнавать его параметром от презентации. Палитра
         * (ColorPicker) стартует с этого значения, поэтому дефолт всегда
         * есть среди предложенных кружков.
         */
        const val DEFAULT_COLOR: Long = 0xFFE53935
    }
}
