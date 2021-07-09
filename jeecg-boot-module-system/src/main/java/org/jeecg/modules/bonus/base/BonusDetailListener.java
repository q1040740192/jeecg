package org.jeecg.modules.bonus.base;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.log4j.Log4j;
import org.jeecg.modules.bonus.entity.BonusDetail;
import org.jeecg.modules.bonus.entity.BonusSysFile;
import org.jeecg.modules.bonus.service.IBonusDetailLogService;
import org.jeecg.modules.bonus.service.IBonusDetailService;
import org.jeecg.modules.bonus.service.IBonusSysFileService;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;



public class BonusDetailListener extends AnalysisEventListener<BonusDetail> {

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

    /**
     * 每隔5条存储数据库，实际使用中可以3000条，然后清理list ，方便内存回收
     */
    private static final int BATCH_COUNT = 500;
    List<BonusDetail> list = new ArrayList<BonusDetail>();


    /**
     * 假设这个是一个DAO，当然有业务逻辑这个也可以是一个service。当然如果不用存储这个对象没用。
     */
    private IBonusDetailService detailService;
    private IBonusDetailLogService logService;
    private BonusSysFile sysFile;
    private IBonusSysFileService fileService;

    /**
     * 如果使用了spring,请使用这个构造方法。每次创建Listener的时候需要把spring管理的类传进来
     *
     * @param detailService
     */
    public BonusDetailListener(IBonusDetailService detailService, IBonusDetailLogService logService,
                               BonusSysFile sysFile, IBonusSysFileService fileService) {
        this.detailService = detailService;
        this.logService = logService;
        this.fileService = fileService;
        this.sysFile = sysFile;
    }

    public BonusDetailListener(IBonusDetailService detailService, IBonusDetailLogService logService,BonusSysFile sysFile) {
        this.detailService = detailService;
        this.logService = logService;
        this.sysFile = sysFile;
    }

    public BonusDetailListener(IBonusDetailService detailService, IBonusDetailLogService logService) {
        this.detailService = detailService;
        this.logService = logService;
    }

    public BonusDetailListener(IBonusDetailService detailService) {
        this.detailService = detailService;
    }


    //这个每一条数据解析都会来调用
    @Override
    public void invoke(BonusDetail data, AnalysisContext analysisContext) {
        System.out.println("解析到一条数据,bonusDetail:"+data);
        //填充一个默认查询时间
//        data.setStandarddate(data.getYear()+"-"+data.getMonth()+"-01");
//        data.setOrderCol(dateFormat.format(new Date()));
        //判断当前数据是否已有数据

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("jobNum",data.getJobnum());
        queryWrapper.eq("year",data.getYear());
        queryWrapper.eq("month",data.getMonth());
        //查询数据的id
        BonusDetail one = detailService.getOne(queryWrapper);
        //填充id
        if (one != null)
            data.setId(one.getId());

        list.add(data);
        // 达到BATCH_COUNT了，需要去存储一次数据库，防止数据几万条数据在内存，容易OOM
        if (list.size() >= BATCH_COUNT) {
            saveData();
            // 存储完成清理 list
            list.clear();
        }
    }


    //所有数据解析完成了 都会来调用
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

        // 这里也要保存数据，确保最后遗留的数据也存储到数据库
        saveData();

        if (sysFile.getState() == null){
            sysFile.setState(1);//设置成功
        }
//        sysFile.setEndtime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        boolean b = fileService.updateById(sysFile);//修改数据库解析状态
        if (!b){
            System.out.println("文件名：["+sysFile.getFilename()+"]<=======>系统名：["+sysFile.getFilesysno()+"]更新失败！ ");
        }
        System.out.println("所有数据解析完成");

    }

    //加上存储数据库
    private void saveData() {
        boolean b = true;
        try {
            //
            ArrayList<BonusDetail> collect = list.stream().sorted(Comparator.comparing(BonusDetail::getOrderCol,Comparator.reverseOrder())).collect(
                    Collectors.collectingAndThen(
                            Collectors.toCollection(
                                    () -> new TreeSet<>(Comparator.comparing(x -> x.getJobnum() + "-" + x.getYear() + "-" + x.getMonth())
                                    )
                            ), ArrayList::new
                    )
            );
            //
            b = detailService.saveOrUpdateBatch(collect);
        }catch (Exception e){
            e.printStackTrace();
            b = false;
        }
        if (b) System.out.println("数据库插入失败");

//        if (!b){
//            log.error("当前集合数据插入数据库失败：["+JSON.toJSONString(list)+"]");
//            List<DetailLog> logs = new ArrayList<>();
//            list.stream().forEach(x->{
//                DetailLog detailLog = JSONObject.parseObject(JSON.toJSONString(x), DetailLog.class);
//                detailLog.setFileSysNo(sysFile.getFileSysNo());
//                logs.add(detailLog);
//            });
//            boolean b1 = logService.saveBatch(logs);
//            if (!b1){
//                log.error("日志数据：["+JSON.toJSONString(list)+"]");
//            }
//            sysFile.setState(0);
//        }
    }


}
