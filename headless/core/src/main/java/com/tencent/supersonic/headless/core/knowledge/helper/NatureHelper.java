package com.tencent.supersonic.headless.core.knowledge.helper;

import com.google.common.collect.Lists;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.tencent.supersonic.common.pojo.enums.DictWordType;
import com.tencent.supersonic.headless.api.pojo.SchemaElementType;
import com.tencent.supersonic.headless.api.pojo.response.S2Term;
import com.tencent.supersonic.headless.core.knowledge.ViewInfoStat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * nature parse helper
 */
@Slf4j
public class NatureHelper {

    public static SchemaElementType convertToElementType(String nature) {
        DictWordType dictWordType = DictWordType.getNatureType(nature);
        if (Objects.isNull(dictWordType)) {
            return null;
        }
        SchemaElementType result = null;
        switch (dictWordType) {
            case METRIC:
                result = SchemaElementType.METRIC;
                break;
            case DIMENSION:
                result = SchemaElementType.DIMENSION;
                break;
            case ENTITY:
                result = SchemaElementType.ENTITY;
                break;
            case VIEW:
                result = SchemaElementType.VIEW;
                break;
            case VALUE:
                result = SchemaElementType.VALUE;
                break;
            case TAG:
                result = SchemaElementType.TAG;
                break;
            default:
                break;
        }
        return result;
    }

    private static boolean isViewOrEntity(S2Term term, Integer model) {
        return (DictWordType.NATURE_SPILT + model).equals(term.nature.toString()) || term.nature.toString()
                .endsWith(DictWordType.ENTITY.getType());
    }

    public static Integer getViewByNature(Nature nature) {
        if (nature.startsWith(DictWordType.NATURE_SPILT)) {
            String[] dimensionValues = nature.toString().split(DictWordType.NATURE_SPILT);
            if (StringUtils.isNumeric(dimensionValues[1])) {
                return Integer.valueOf(dimensionValues[1]);
            }
        }
        return 0;
    }

    public static Long getViewId(String nature) {
        try {
            String[] split = nature.split(DictWordType.NATURE_SPILT);
            if (split.length <= 1) {
                return null;
            }
            return Long.valueOf(split[1]);
        } catch (NumberFormatException e) {
            log.error("", e);
        }
        return null;
    }

    private static Long getModelId(String nature) {
        try {
            String[] split = nature.split(DictWordType.NATURE_SPILT);
            if (split.length <= 1) {
                return null;
            }
            return Long.valueOf(split[1]);
        } catch (NumberFormatException e) {
            log.error("", e);
        }
        return null;
    }

    private static Nature changeModel2View(String nature, Long viewId) {
        try {
            String[] split = nature.split(DictWordType.NATURE_SPILT);
            if (split.length <= 1) {
                return null;
            }
            split[1] = String.valueOf(viewId);
            return Nature.create(StringUtils.join(split, DictWordType.NATURE_SPILT));
        } catch (NumberFormatException e) {
            log.error("", e);
        }
        return null;
    }

    public static List<String> changeModel2View(String nature, Map<Long, List<Long>> modelIdToViewIds) {
        Long modelId = getModelId(nature);
        List<Long> viewIds = modelIdToViewIds.get(modelId);
        if (CollectionUtils.isEmpty(viewIds)) {
            return Lists.newArrayList();
        }
        return viewIds.stream().map(viewId -> String.valueOf(changeModel2View(nature, viewId)))
                .collect(Collectors.toList());
    }

    public static boolean isDimensionValueViewId(String nature) {
        if (StringUtils.isEmpty(nature)) {
            return false;
        }
        if (!nature.startsWith(DictWordType.NATURE_SPILT)) {
            return false;
        }
        String[] split = nature.split(DictWordType.NATURE_SPILT);
        if (split.length <= 1) {
            return false;
        }
        return !nature.endsWith(DictWordType.METRIC.getType()) && !nature.endsWith(DictWordType.DIMENSION.getType())
                && StringUtils.isNumeric(split[1]);
    }

    public static ViewInfoStat getViewStat(List<S2Term> terms) {
        return ViewInfoStat.builder()
                .viewCount(getViewCount(terms))
                .dimensionViewCount(getDimensionCount(terms))
                .metricViewCount(getMetricCount(terms))
                .dimensionValueViewCount(getDimensionValueCount(terms))
                .build();
    }

    private static long getViewCount(List<S2Term> terms) {
        return terms.stream().filter(term -> isViewOrEntity(term, getViewByNature(term.nature))).count();
    }

    private static long getDimensionValueCount(List<S2Term> terms) {
        return terms.stream().filter(term -> isDimensionValueViewId(term.nature.toString())).count();
    }

    private static long getDimensionCount(List<S2Term> terms) {
        return terms.stream().filter(term -> term.nature.startsWith(DictWordType.NATURE_SPILT) && term.nature.toString()
                .endsWith(DictWordType.DIMENSION.getType())).count();
    }

    private static long getMetricCount(List<S2Term> terms) {
        return terms.stream().filter(term -> term.nature.startsWith(DictWordType.NATURE_SPILT) && term.nature.toString()
                .endsWith(DictWordType.METRIC.getType())).count();
    }

    /**
     * Get the number of types of class parts of speech
     * modelId -> (nature , natureCount)
     *
     * @param terms
     * @return
     */
    public static Map<Long, Map<DictWordType, Integer>> getViewToNatureStat(List<S2Term> terms) {
        Map<Long, Map<DictWordType, Integer>> modelToNature = new HashMap<>();
        terms.stream().filter(
                term -> term.nature.startsWith(DictWordType.NATURE_SPILT)
        ).forEach(term -> {
            DictWordType dictWordType = DictWordType.getNatureType(String.valueOf(term.nature));
            Long model = getViewId(String.valueOf(term.nature));

            Map<DictWordType, Integer> natureTypeMap = new HashMap<>();
            natureTypeMap.put(dictWordType, 1);

            Map<DictWordType, Integer> original = modelToNature.get(model);
            if (Objects.isNull(original)) {
                modelToNature.put(model, natureTypeMap);
            } else {
                Integer count = original.get(dictWordType);
                if (Objects.isNull(count)) {
                    count = 1;
                } else {
                    count = count + 1;
                }
                original.put(dictWordType, count);
            }
        });
        return modelToNature;
    }

    public static List<Long> selectPossibleViews(List<S2Term> terms) {
        Map<Long, Map<DictWordType, Integer>> modelToNatureStat = getViewToNatureStat(terms);
        Integer maxViewTypeSize = modelToNatureStat.entrySet().stream()
                .max(Comparator.comparingInt(o -> o.getValue().size())).map(entry -> entry.getValue().size())
                .orElse(null);
        if (Objects.isNull(maxViewTypeSize) || maxViewTypeSize == 0) {
            return new ArrayList<>();
        }
        return modelToNatureStat.entrySet().stream().filter(entry -> entry.getValue().size() == maxViewTypeSize)
                .map(entry -> entry.getKey()).collect(Collectors.toList());
    }

    public static Long getElementID(String nature) {
        String[] split = nature.split(DictWordType.NATURE_SPILT);
        if (split.length >= 3) {
            return Long.valueOf(split[2]);
        }
        return 0L;
    }

}
