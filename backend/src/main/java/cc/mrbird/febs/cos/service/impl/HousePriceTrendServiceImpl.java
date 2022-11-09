package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.cos.entity.HousePriceTrend;
import cc.mrbird.febs.cos.dao.HousePriceTrendMapper;
import cc.mrbird.febs.cos.service.IHousePriceTrendService;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author FanK
 */
@Service
public class HousePriceTrendServiceImpl extends ServiceImpl<HousePriceTrendMapper, HousePriceTrend> implements IHousePriceTrendService {

    /**
     * 分页获取房价走势信息
     *
     * @param page            分页对象
     * @param housePriceTrend 房价走势信息
     * @return 结果
     */
    @Override
    public IPage<LinkedHashMap<String, Object>> selectPriceTrendPage(Page<HousePriceTrend> page, HousePriceTrend housePriceTrend) {
        return baseMapper.selectPriceTrendPage(page, housePriceTrend);
    }

    /**
     * 获取房价走势信息
     *
     * @param communityCode 小区编号
     * @param year          年
     * @param month         月
     * @return 结果
     */
    @Override
    public LinkedHashMap<String, BigDecimal> selectHousePriceTrend(String communityCode, String year, String month) {
        String type = StrUtil.isEmpty(month) ? "1" : "2";
        List<HousePriceTrend> houseTrendList = baseMapper.selectPriceTrendByCommunityCode(communityCode);
        if (CollectionUtil.isEmpty(houseTrendList)) {
            return null;
        }
        List<String> dateList = new ArrayList<>();
        switch (type) {
            case "1":
                int currentYear = DateUtil.year(new Date());
                dateList = new ArrayList<String>(Arrays.asList(StrUtil.toString(year), StrUtil.toString(currentYear - 1), StrUtil.toString(currentYear - 2), StrUtil.toString(currentYear - 3), StrUtil.toString(currentYear - 4), StrUtil.toString(currentYear - 5)));
                break;
            case "2":
                dateList = new ArrayList<String>(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
                break;
            default:
        }
        LinkedHashMap<String, BigDecimal> housePriceMap = new LinkedHashMap<>(16);
        if ("1".equals(type)) {
            Map<String, List<HousePriceTrend>> housePriceTrendMap = houseTrendList.stream().collect(Collectors.groupingBy(HousePriceTrend::getYear));
            for (String date : dateList) {
                List<HousePriceTrend> housePriceTrendList = housePriceTrendMap.get(date);
                if (CollectionUtil.isEmpty(housePriceTrendList)) {
                    housePriceMap.put(date, BigDecimal.ZERO);
                    continue;
                }
                BigDecimal price = housePriceTrendList.stream().map(HousePriceTrend::getHousePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                housePriceMap.put(date, price.divide(BigDecimal.valueOf(housePriceTrendList.size()), 0, RoundingMode.HALF_UP));
            }
        } else {
            houseTrendList = houseTrendList.stream().filter(e -> year.equals(e.getYear())).collect(Collectors.toList());
            // 按照月度分组
            Map<String, List<HousePriceTrend>> housePriceTrendMap = houseTrendList.stream().collect(Collectors.groupingBy(HousePriceTrend::getMonth));
            for (String date : dateList) {
                List<HousePriceTrend> housePriceTrendList = housePriceTrendMap.get(date);
                if (CollectionUtil.isEmpty(housePriceTrendList)) {
                    housePriceMap.put(date, BigDecimal.ZERO);
                    continue;
                }
                BigDecimal price = housePriceTrendList.stream().map(HousePriceTrend::getHousePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                housePriceMap.put(date, price.divide(BigDecimal.valueOf(housePriceTrendList.size()), 0, RoundingMode.HALF_UP));
            }
        }
        return housePriceMap;
    }
}
