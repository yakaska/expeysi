package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType

internal fun AccountEntity.selectableId(): String? = id.takeUnless { deleted }

internal fun CategoryEntity.selectableId(type: TxType): String? = id.takeUnless { deleted || this.type != type.wire }
