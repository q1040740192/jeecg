package org.jeecg.modules.bonus.base;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.boot.starter.rabbitmq.core.BaseRabbiMqHandler;
import org.jeecg.boot.starter.rabbitmq.listenter.MqListener;
import org.jeecg.common.annotation.RabbitComponent;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.modules.bonus.entity.BonusDetail;
import org.jeecg.modules.bonus.entity.BonusDetailLog;
import org.jeecg.modules.bonus.entity.BonusSysFile;
import org.jeecg.modules.bonus.service.IBonusDetailLogService;
import org.jeecg.modules.bonus.service.IBonusDetailService;
import org.jeecg.modules.bonus.service.IBonusSysFileService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import com.rabbitmq.client.Channel;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.hibernate.loader.internal.AliasConstantsHelper.get;

@Slf4j
@RabbitComponent(value = "BonusFileMQ")
public class BonusFileMQ extends BaseRabbiMqHandler<BaseMap> {


    @Autowired
    IBonusDetailService detailService;

    @Autowired
    IBonusDetailLogService logService;

    @Autowired
    IBonusSysFileService fileService;


    @RabbitListener(queues = "bonus")
    @RabbitHandler
    public void onMessage(BaseMap baseMap, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        super.onMessage(baseMap, deliveryTag, channel, new MqListener<BaseMap>() {
            @Override
            public void handler(BaseMap map, com.rabbitmq.client.Channel channel) {
                //业务处理
                BonusSysFile sysFile = map.get("bonusFile");//对应文件对象
                List<BonusDetail> list = map.get("list");//对应解析的激励金对象
                sysFile.setRuntime(new Date());
//                String fileName = sysFile.getFilepath();//文件地址
                List<BonusDetail> list500 = new ArrayList<BonusDetail>();
                try {
                    for (int i = 0; i < list.size(); i++) {
                        BonusDetail bonusDetail = list.get(i);//list
                        //判断当前数据是否已有数据
                        QueryWrapper queryWrapper = new QueryWrapper();
                        queryWrapper.eq("jobNum", bonusDetail.getJobnum());
                        queryWrapper.eq("year", bonusDetail.getYear());
                        queryWrapper.eq("month", bonusDetail.getMonth());
                        //查询数据的id
                        BonusDetail one = detailService.getOne(queryWrapper);
                        //如果数据库没有记录，填充id
                        if (one != null) {
                            bonusDetail.setId(one.getId());
                        }
                        list500.add(bonusDetail);
                    }
                    boolean b = saveData(sysFile, list500);//讲解析的文件保存到数据库
                    // 存储完成清理 list
                    list.clear();
                    log.info("文件解析成功");
                    if (b) {
                        sysFile.setState(1);//改变文件状态，设置1为成功}
                    } else {
                        sysFile.setState(0);//改变文件状态，设置0为失败
                    }
                    fileService.updateById(sysFile);
                } catch (Exception e) {
                    list.clear();
                    log.info("文件解析失败");
                    e.printStackTrace();
                    sysFile.setState(0);//改变文件状态，设置0为失败
                    fileService.updateById(sysFile);
                }

            }
        });
    }


    /**
     * 加上存储数据库
     */
    private boolean saveData(BonusSysFile sysFile, List<BonusDetail> list500) throws Exception {
        //将数据保存到数据库
        boolean b = detailService.saveOrUpdateBatch(list500);

        if (!b) {//如果保存失败
            log.error("当前集合数据插入数据库失败：[" + list500 + "]");
            List<BonusDetailLog> logs = new ArrayList<>();
            for (int i = 0; i < list500.size(); i++) {
                //将插入失败的数据保存到log
                BonusDetailLog bonusDetailLog = new BonusDetailLog();
                BeanUtils.copyProperties(list500.get(i), bonusDetailLog);
                System.out.println("转换对象为：" + bonusDetailLog);
                //将系统名也设置到日志里
                bonusDetailLog.setFilesysno(sysFile.getFilesysno());
                //添加logs

            }
            boolean b1 = logService.saveBatch(logs);//将数据插入失败的插入日志
            if (!b1) {
                log.error("日志数据：[" + list500 + "]");
                return b1;
            }
        }
        return b;
    }
}
