package com.designtocode.domain.port

import com.designtocode.domain.model.QualityGateResult

interface QualityGatePort {
    fun validate(): QualityGateResult
}
