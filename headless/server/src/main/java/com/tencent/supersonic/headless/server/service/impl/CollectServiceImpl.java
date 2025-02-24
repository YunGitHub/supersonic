package com.tencent.supersonic.headless.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.headless.server.persistence.dataobject.CollectDO;
import com.tencent.supersonic.headless.server.persistence.mapper.CollectMapper;
import com.tencent.supersonic.headless.server.service.CollectService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@Service
public class CollectServiceImpl implements CollectService {

    public static final String type = "metric";
    @Resource
    private CollectMapper collectMapper;

    @Override
    public Boolean createCollectionIndicators(User user, Long id) {
        CollectDO collectDO = new CollectDO();
        collectDO.setType(type);
        collectDO.setUsername(user.getName());
        collectDO.setCollectId(id);
        collectMapper.insert(collectDO);
        return true;
    }

    @Override
    public Boolean deleteCollectionIndicators(User user, Long id) {
        QueryWrapper<CollectDO> collectDOQueryWrapper = new QueryWrapper<>();
        collectDOQueryWrapper.lambda().eq(CollectDO::getUsername, user.getName());
        collectDOQueryWrapper.lambda().eq(CollectDO::getCollectId, id);
        collectDOQueryWrapper.lambda().eq(CollectDO::getType, type);
        collectMapper.delete(collectDOQueryWrapper);
        return true;
    }

    @Override
    public List<CollectDO> getCollectList(String username) {
        QueryWrapper<CollectDO> queryWrapper = new QueryWrapper<>();
        if (!StringUtils.isEmpty(username)) {
            queryWrapper.lambda().eq(CollectDO::getUsername, username);
        }
        return collectMapper.selectList(queryWrapper);
    }
}
