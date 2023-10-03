/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef HB_JDK_H
#define HB_JDK_H

#define JDKEXPORT  __attribute__((visibility("default")))
#ifndef JDKEXPORT
  #ifdef WIN32
    #define JDKEXPORT __declspec(dllexport)
  #endif
#else
  #if (defined(__GNUC__) && ((__GNUC__ > 4) || (__GNUC__ == 4) && (__GNUC_MINOR__ > 2))) || __has_attribute(visibility)
    #ifdef ARM
      #define JDKEXPORT  __attribute__((externally_visible,visibility("default")))
    #else
      #define JDKEXPORT  __attribute__((visibility("default")))
    #endif
  #else
    #define JDKEXPORT
  #endif
#endif

#include "hb.h"

# ifdef __cplusplus
extern "C" {
#endif


hb_font_t* jdk_font_create_hbp(
               hb_face_t* face,
               float ptSize, float devScale,
               hb_destroy_func_t destroy,
               hb_font_funcs_t* font_funcs);


typedef int (*store_layoutdata_func_t)
   (int slot, int baseIndex, int offset,
    float startX, float startY, float devScale,
    int charCount, int glyphCount,
    hb_glyph_info_t *glyphInfo, hb_glyph_position_t *glyphPos);

JDKEXPORT int jdk_hb_shape(
     float ptSize,
     float *matrix,
     void* pFace,
     unsigned short *chars,
     int len,
     int script,
     int offset,
     int limit,
     int baseIndex,
     float startX,
     float startY,
     int flags,
     int slot,
     hb_font_funcs_t* font_funcs,
     store_layoutdata_func_t store_layout_results_fn
    );

JDKEXPORT hb_font_funcs_t * jdk_get_hb_font_funcs(hb_font_get_nominal_glyph_func_t nominal_fn,
                                 hb_font_get_variation_glyph_func_t variation_fn,
                                 hb_font_get_glyph_h_advance_func_t h_advance_fn,
                                 hb_font_get_glyph_v_advance_func_t v_advance_fn,
                                 hb_font_get_glyph_contour_point_func_t contour_pt_fn);

# ifdef __cplusplus
}
#endif

#endif /* HB_JDK_H */
