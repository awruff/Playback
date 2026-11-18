package org.polyfrost.keyframe.compat;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class LatticeCanceller implements MixinCanceller {

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        //? if >=1.21.10 && <26.1 {
        return mixinClassName.equals("com.moulberry.lattice.mixin.v1219.MixinDropdownWidgetEntry");
        //?} else
        /*return false;*/
    }

}
