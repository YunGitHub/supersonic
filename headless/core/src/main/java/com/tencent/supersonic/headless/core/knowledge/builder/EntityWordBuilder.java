package com.tencent.supersonic.headless.core.knowledge.builder;


import com.google.common.collect.Lists;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.core.knowledge.DictWord;
import com.tencent.supersonic.common.pojo.enums.DictWordType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * dimension value wordNature
 */
@Service
@Slf4j
public class EntityWordBuilder extends BaseWordBuilder {

    @Override
    public List<DictWord> doGet(String word, SchemaElement schemaElement) {
        List<DictWord> result = Lists.newArrayList();

        if (Objects.isNull(schemaElement)) {
            return result;
        }

        Long modelId = schemaElement.getModel();
        String nature = DictWordType.NATURE_SPILT + modelId + DictWordType.NATURE_SPILT + schemaElement.getId()
                + DictWordType.ENTITY.getType();

        if (!CollectionUtils.isEmpty(schemaElement.getAlias())) {
            schemaElement.getAlias().stream().forEach(alias -> {
                DictWord dictWordAlias = new DictWord();
                dictWordAlias.setWord(alias);
                dictWordAlias.setNatureWithFrequency(String.format("%s " + DEFAULT_FREQUENCY * 2, nature));
                result.add(dictWordAlias);
            });
        }
        return result;
    }

}
