package com.tencent.supersonic.headless.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.DataEvent;
import com.tencent.supersonic.common.pojo.DataItem;
import com.tencent.supersonic.common.pojo.ModelRela;
import com.tencent.supersonic.common.pojo.enums.EventType;
import com.tencent.supersonic.common.pojo.enums.StatusEnum;
import com.tencent.supersonic.common.pojo.enums.TypeEnums;
import com.tencent.supersonic.common.pojo.exception.InvalidArgumentException;
import com.tencent.supersonic.common.util.ChatGptHelper;
import com.tencent.supersonic.headless.api.pojo.DimValueMap;
import com.tencent.supersonic.headless.api.pojo.ModelDetail;
import com.tencent.supersonic.headless.api.pojo.request.DimensionReq;
import com.tencent.supersonic.headless.api.pojo.request.MetaBatchReq;
import com.tencent.supersonic.headless.api.pojo.request.PageDimensionReq;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.api.pojo.response.DimensionResp;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.api.pojo.response.SemanticQueryResp;
import com.tencent.supersonic.headless.api.pojo.response.ViewResp;
import com.tencent.supersonic.headless.server.persistence.dataobject.DimensionDO;
import com.tencent.supersonic.headless.server.persistence.repository.DimensionRepository;
import com.tencent.supersonic.headless.server.pojo.DimensionFilter;
import com.tencent.supersonic.headless.server.pojo.DimensionsFilter;
import com.tencent.supersonic.headless.server.pojo.MetaFilter;
import com.tencent.supersonic.headless.server.service.DatabaseService;
import com.tencent.supersonic.headless.server.service.DimensionService;
import com.tencent.supersonic.headless.server.service.ModelRelaService;
import com.tencent.supersonic.headless.server.service.ModelService;
import com.tencent.supersonic.headless.server.service.ViewService;
import com.tencent.supersonic.headless.server.utils.DimensionConverter;
import com.tencent.supersonic.headless.server.utils.NameCheckUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
public class DimensionServiceImpl implements DimensionService {


    private DimensionRepository dimensionRepository;

    private ModelService modelService;

    private ChatGptHelper chatGptHelper;

    private DatabaseService databaseService;

    private ModelRelaService modelRelaService;

    private ViewService viewService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;


    public DimensionServiceImpl(DimensionRepository dimensionRepository,
            ModelService modelService,
            ChatGptHelper chatGptHelper,
            DatabaseService databaseService,
            ModelRelaService modelRelaService,
            ViewService viewService) {
        this.modelService = modelService;
        this.dimensionRepository = dimensionRepository;
        this.chatGptHelper = chatGptHelper;
        this.databaseService = databaseService;
        this.modelRelaService = modelRelaService;
        this.viewService = viewService;
    }

    @Override
    public void createDimension(DimensionReq dimensionReq, User user) {
        checkExist(Lists.newArrayList(dimensionReq));
        dimensionReq.createdBy(user.getName());
        DimensionDO dimensionDO = DimensionConverter.convert2DimensionDO(dimensionReq);
        dimensionRepository.createDimension(dimensionDO);
        sendEventBatch(Lists.newArrayList(dimensionDO), EventType.ADD);
    }

    @Override
    public void createDimensionBatch(List<DimensionReq> dimensionReqs, User user) {
        if (CollectionUtils.isEmpty(dimensionReqs)) {
            return;
        }
        Long modelId = dimensionReqs.get(0).getModelId();
        List<DimensionResp> dimensionResps = getDimensions(modelId);
        Map<String, DimensionResp> bizNameMap = dimensionResps.stream()
                .collect(Collectors.toMap(DimensionResp::getBizName, a -> a, (k1, k2) -> k1));
        Map<String, DimensionResp> nameMap = dimensionResps.stream()
                .collect(Collectors.toMap(DimensionResp::getName, a -> a, (k1, k2) -> k1));
        List<DimensionReq> dimensionToInsert = dimensionReqs.stream()
                .filter(dimension -> !bizNameMap.containsKey(dimension.getBizName())
                        && !nameMap.containsKey(dimension.getName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(dimensionToInsert)) {
            return;
        }
        List<DimensionDO> dimensionDOS = dimensionToInsert.stream().peek(dimension ->
                        dimension.createdBy(user.getName()))
                .map(DimensionConverter::convert2DimensionDO)
                .collect(Collectors.toList());
        dimensionRepository.createDimensionBatch(dimensionDOS);
        sendEventBatch(dimensionDOS, EventType.ADD);
    }

    @Override
    public void updateDimension(DimensionReq dimensionReq, User user) {
        checkExist(Lists.newArrayList(dimensionReq));
        DimensionDO dimensionDO = dimensionRepository.getDimensionById(dimensionReq.getId());
        dimensionReq.updatedBy(user.getName());
        String oldName = dimensionDO.getName();
        DimensionConverter.convert(dimensionDO, dimensionReq);
        dimensionRepository.updateDimension(dimensionDO);
        if (!oldName.equals(dimensionDO.getName())) {
            sendEvent(DataItem.builder().modelId(dimensionDO.getModelId() + Constants.UNDERLINE)
                    .newName(dimensionReq.getName()).name(oldName).type(TypeEnums.DIMENSION)
                    .id(dimensionDO.getId() + Constants.UNDERLINE).build(), EventType.UPDATE);
        }
    }

    @Override
    public void batchUpdateStatus(MetaBatchReq metaBatchReq, User user) {
        if (CollectionUtils.isEmpty(metaBatchReq.getIds())) {
            return;
        }
        DimensionFilter dimensionFilter = new DimensionFilter();
        dimensionFilter.setIds(metaBatchReq.getIds());
        List<DimensionDO> dimensionDOS = dimensionRepository.getDimension(dimensionFilter);
        if (CollectionUtils.isEmpty(dimensionDOS)) {
            return;
        }
        dimensionDOS = dimensionDOS.stream()
                .peek(dimensionDO -> {
                    dimensionDO.setStatus(metaBatchReq.getStatus());
                    dimensionDO.setUpdatedAt(new Date());
                    dimensionDO.setUpdatedBy(user.getName());
                })
                .collect(Collectors.toList());
        dimensionRepository.batchUpdateStatus(dimensionDOS);
        if (StatusEnum.OFFLINE.getCode().equals(metaBatchReq.getStatus())
                || StatusEnum.DELETED.getCode().equals(metaBatchReq.getStatus())) {
            sendEventBatch(dimensionDOS, EventType.DELETE);
        } else if (StatusEnum.ONLINE.getCode().equals(metaBatchReq.getStatus())) {
            sendEventBatch(dimensionDOS, EventType.ADD);
        }
    }

    @Override
    public void deleteDimension(Long id, User user) {
        DimensionDO dimensionDO = dimensionRepository.getDimensionById(id);
        if (dimensionDO == null) {
            throw new RuntimeException(String.format("the dimension %s not exist", id));
        }
        dimensionDO.setStatus(StatusEnum.DELETED.getCode());
        dimensionDO.setUpdatedAt(new Date());
        dimensionDO.setUpdatedBy(user.getName());
        dimensionRepository.updateDimension(dimensionDO);
        sendEventBatch(Lists.newArrayList(dimensionDO), EventType.DELETE);
    }

    @Override
    public DimensionResp getDimension(String bizName, Long modelId) {
        List<DimensionResp> dimensionResps = getDimensions(modelId);
        if (CollectionUtils.isEmpty(dimensionResps)) {
            return null;
        }
        for (DimensionResp dimensionResp : dimensionResps) {
            if (dimensionResp.getBizName().equalsIgnoreCase(bizName)) {
                return dimensionResp;
            }
        }
        return null;
    }

    @Override
    public DimensionResp getDimension(Long id) {
        DimensionDO dimensionDO = dimensionRepository.getDimensionById(id);
        if (dimensionDO == null) {
            return null;
        }
        return DimensionConverter.convert2DimensionResp(dimensionDO, new HashMap<>());
    }

    @Override
    public PageInfo<DimensionResp> queryDimension(PageDimensionReq pageDimensionReq) {
        DimensionFilter dimensionFilter = new DimensionFilter();
        BeanUtils.copyProperties(pageDimensionReq, dimensionFilter);
        dimensionFilter.setModelIds(pageDimensionReq.getModelIds());
        PageInfo<DimensionDO> dimensionDOPageInfo = PageHelper.startPage(pageDimensionReq.getCurrent(),
                        pageDimensionReq.getPageSize())
                .doSelectPageInfo(() -> queryDimension(dimensionFilter));
        PageInfo<DimensionResp> pageInfo = new PageInfo<>();
        BeanUtils.copyProperties(dimensionDOPageInfo, pageInfo);
        pageInfo.setList(convertList(dimensionDOPageInfo.getList(), modelService.getModelMap()));
        return pageInfo;
    }

    private List<DimensionDO> queryDimension(DimensionFilter dimensionFilter) {
        return dimensionRepository.getDimension(dimensionFilter);
    }

    @Override
    public List<DimensionResp> queryDimensions(DimensionsFilter dimensionsFilter) {
        List<DimensionDO> dimensions = dimensionRepository.getDimensions(dimensionsFilter);
        return convertList(dimensions, modelService.getModelMap());
    }

    @Override
    public List<DimensionResp> getDimensions(MetaFilter metaFilter) {
        DimensionFilter dimensionFilter = new DimensionFilter();
        BeanUtils.copyProperties(metaFilter, dimensionFilter);
        List<DimensionDO> dimensionDOS = dimensionRepository.getDimension(dimensionFilter);
        List<DimensionResp> dimensionResps = convertList(dimensionDOS, modelService.getModelMap());
        if (!CollectionUtils.isEmpty(metaFilter.getFieldsDepend())) {
            return filterByField(dimensionResps, metaFilter.getFieldsDepend());
        }
        if (metaFilter.getViewId() != null) {
            ViewResp viewResp = viewService.getView(metaFilter.getViewId());
            return DimensionConverter.filterByView(dimensionResps, viewResp);
        }
        return dimensionResps;
    }

    private List<DimensionResp> getDimensions(Long modelId) {
        return getDimensions(new MetaFilter(Lists.newArrayList(modelId)));
    }

    private List<DimensionResp> filterByField(List<DimensionResp> dimensionResps, List<String> fields) {
        List<DimensionResp> dimensionFiltered = Lists.newArrayList();
        for (DimensionResp dimensionResp : dimensionResps) {
            for (String field : fields) {
                if (dimensionResp.getExpr().contains(field)) {
                    dimensionFiltered.add(dimensionResp);
                }
            }
        }
        return dimensionFiltered;
    }

    @Override
    public List<DimensionResp> getDimensionInModelCluster(Long modelId) {
        ModelResp modelResp = modelService.getModel(modelId);
        List<ModelRela> modelRelas = modelRelaService.getModelRelaList(modelResp.getDomainId());
        List<Long> modelIds = new ArrayList<>();
        modelIds.add(modelId);
        for (ModelRela modelRela : modelRelas) {
            modelIds.add(modelRela.getFromModelId());
            modelIds.add(modelRela.getToModelId());
        }
        DimensionFilter dimensionFilter = new DimensionFilter();
        dimensionFilter.setModelIds(modelIds);
        return getDimensions(dimensionFilter);
    }

    private List<DimensionResp> convertList(List<DimensionDO> dimensionDOS,
            Map<Long, ModelResp> modelRespMap) {
        List<DimensionResp> dimensionResps = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(dimensionDOS)) {
            dimensionResps = dimensionDOS.stream()
                    .map(dimensionDO -> DimensionConverter
                            .convert2DimensionResp(dimensionDO, modelRespMap))
                    .collect(Collectors.toList());
        }
        return dimensionResps;
    }

    @Override
    public List<String> mockAlias(DimensionReq dimensionReq, String mockType, User user) {
        String mockAlias = chatGptHelper.mockAlias(mockType, dimensionReq.getName(), dimensionReq.getBizName(),
                "", dimensionReq.getDescription(), false);
        return JSONObject.parseObject(mockAlias, new TypeReference<List<String>>() {
        });
    }

    @Override
    public List<DimValueMap> mockDimensionValueAlias(DimensionReq dimensionReq, User user) {
        ModelResp modelResp = modelService.getModel(dimensionReq.getModelId());
        ModelDetail modelDetail = modelResp.getModelDetail();
        String sqlQuery = modelDetail.getSqlQuery();
        DatabaseResp database = databaseService.getDatabase(modelResp.getDatabaseId());

        String sql = "select ai_talk." + dimensionReq.getBizName() + " from (" + sqlQuery
                + ") as ai_talk group by ai_talk." + dimensionReq.getBizName();
        SemanticQueryResp semanticQueryResp = databaseService.executeSql(sql, database);
        List<Map<String, Object>> resultList = semanticQueryResp.getResultList();
        List<String> valueList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : resultList) {
            String value = (String) stringObjectMap.get(dimensionReq.getBizName());
            valueList.add(value);
        }
        String json = chatGptHelper.mockDimensionValueAlias(JSON.toJSONString(valueList));
        log.info("return llm res is :{}", json);

        JSONObject jsonObject = JSON.parseObject(json);

        List<DimValueMap> dimValueMapsResp = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> stringObjectMap : resultList) {
            DimValueMap dimValueMap = new DimValueMap();
            dimValueMap.setTechName(String.valueOf(stringObjectMap.get(dimensionReq.getBizName())));
            try {
                String tran = jsonObject.getJSONArray("tran").getString(i);
                dimValueMap.setBizName(tran);
            } catch (Exception exception) {
                dimValueMap.setBizName("");
            }
            try {
                dimValueMap.setAlias(jsonObject.getJSONObject("alias")
                        .getJSONArray(stringObjectMap.get(dimensionReq.getBizName()) + "").toJavaList(String.class));
            } catch (Exception exception) {
                dimValueMap.setAlias(null);
            }
            dimValueMapsResp.add(dimValueMap);
            i++;
        }
        return dimValueMapsResp;
    }

    private void checkExist(List<DimensionReq> dimensionReqs) {
        Long modelId = dimensionReqs.get(0).getModelId();
        List<DimensionResp> dimensionResps = getDimensions(modelId);
        Map<String, DimensionResp> bizNameMap = dimensionResps.stream()
                .collect(Collectors.toMap(DimensionResp::getBizName, a -> a, (k1, k2) -> k1));
        Map<String, DimensionResp> nameMap = dimensionResps.stream()
                .collect(Collectors.toMap(DimensionResp::getName, a -> a, (k1, k2) -> k1));
        for (DimensionReq dimensionReq : dimensionReqs) {
            if (NameCheckUtils.containsSpecialCharacters(dimensionReq.getName())) {
                throw new InvalidArgumentException("名称包含特殊字符, 请修改: " + dimensionReq.getName());
            }
            if (bizNameMap.containsKey(dimensionReq.getBizName())) {
                DimensionResp dimensionResp = bizNameMap.get(dimensionReq.getBizName());
                if (!dimensionResp.getId().equals(dimensionReq.getId())) {
                    throw new RuntimeException(String.format("该主题域下存在相同的维度字段名:%s 创建人:%s",
                            dimensionReq.getBizName(), dimensionResp.getCreatedBy()));
                }
            }
            if (nameMap.containsKey(dimensionReq.getName())) {
                DimensionResp dimensionResp = nameMap.get(dimensionReq.getName());
                if (!dimensionResp.getId().equals(dimensionReq.getId())) {
                    throw new RuntimeException(String.format("该主题域下存在相同的维度名:%s 创建人:%s",
                            dimensionReq.getName(), dimensionResp.getCreatedBy()));
                }
            }
        }
    }

    @Override
    public void sendDimensionEventBatch(List<Long> modelIds, EventType eventType) {
        DimensionFilter dimensionFilter = new DimensionFilter();
        dimensionFilter.setModelIds(modelIds);
        List<DimensionDO> dimensionDOS = queryDimension(dimensionFilter);
        sendEventBatch(dimensionDOS, eventType);
    }

    private void sendEventBatch(List<DimensionDO> dimensionDOS, EventType eventType) {
        List<DataItem> dataItems = dimensionDOS.stream()
                .map(dimensionDO -> DataItem.builder().id(dimensionDO.getId() + Constants.UNDERLINE)
                        .name(dimensionDO.getName()).modelId(dimensionDO.getModelId() + Constants.UNDERLINE)
                        .type(TypeEnums.DIMENSION).build())
                .collect(Collectors.toList());
        eventPublisher.publishEvent(new DataEvent(this, dataItems, eventType));
    }

    private void sendEvent(DataItem dataItem, EventType eventType) {
        eventPublisher.publishEvent(new DataEvent(this,
                Lists.newArrayList(dataItem), eventType));
    }

}
