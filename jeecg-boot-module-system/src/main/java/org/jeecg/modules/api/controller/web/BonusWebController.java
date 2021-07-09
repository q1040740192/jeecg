package org.jeecg.modules.api.controller.web;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.JsonObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.boot.starter.rabbitmq.client.RabbitMqClient;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.*;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.bonus.entity.*;
import org.jeecg.modules.bonus.entity.vo.BonusPageVO;
import org.jeecg.modules.bonus.entity.vo.BonusloginVo;
import org.jeecg.modules.bonus.service.*;
import org.jeecg.modules.bonus.utils.DateUtil;
import org.jeecg.modules.system.entity.SysDepart;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.model.SysLoginModel;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

@Api(tags = "激励金web测试")
@Slf4j
@RestController
@RequestMapping("/bonus/web")
public class BonusWebController {

    private static final String BASE_CHECK_CODES = "qwertyuiplkjhgfdsazxcvbnmQWERTYUPLKJHGFDSAZXCVBNM1234567890";

    @Value(value = "${jeecg.path.upload}")
    private String uploadpath;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    IBonusStaffService bonusStaffService;

    @Autowired
    IBonusDetailService bonusDetailService;

    @Autowired
    IBonusSysUserService BonusSysUserService;

    @Autowired
    IBonusTokenService BonusTokenService;

    //查询员工奖励金详情
    @PostMapping("/staffBonus")
    @AutoLog(value = "查询员工奖励金")
    @ApiOperation(value = "查询员工奖励金", notes = "(请求参数:jobNum)根据员工工号查询奖励金详情--结果集为bonus_staff（staffName，jobNum，department，job，phone，detailList）对象，detailList包含多个bonus_detail对象（year，month，moneyAdd，moneyGive，moneyTotal） ----员工工号一个员工信息与所有奖励金信息")
    Result staffBonus(@RequestBody JSONObject jsonObject) {
        String jobNum = jsonObject.getString("jobNum");
        if (StringUtils.isEmpty(jobNum)) return Result.error("参数有误");

        BonusStaff one;

        try {
            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.select("staffName", "jobNum", "department", "job", "phone");
            queryWrapper.eq("jobNum", jobNum);
            one = bonusStaffService.getOne(queryWrapper);//根据工号查询员工信息
            if (one == null) {
                one = new BonusStaff();
//                one.setJobnum(jobNum);
//                bonusStaffService.save(one);//如果没有员工信息，就创建员工
            }
            QueryWrapper detailWrapper = new QueryWrapper();
            detailWrapper.select("jobNum","year", "month", "money_add", "money_give", "money_total");
            detailWrapper.eq("jobNum", jobNum);
            List<BonusDetail> lists = bonusDetailService.list(detailWrapper);
            one.setDetailList(lists);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(10004, "系统异常");
        }
        return Result.OK("查询成功", one);
    }

    //登陆账号
//    @PostMapping("/login")
//    @AutoLog(value = "web端登录")
//    @ApiOperation(value = "web登录接口", notes = "账号密码登录--结果集为bonus_staff对象")
//    @Transactional
//    Result login(@RequestBody BonusSysUser loginUser) {
//
//        if (loginUser == null) return Result.error("参数有误");
//
//        String loginusername = loginUser.getUsername();
//        String loginpas = loginUser.getPassword();
//        if (StringUtils.isEmpty(loginusername) && StringUtils.isEmpty(loginpas))
//            return Result.error("账号或密码不能为空");
//
//        //当前接口时间
//        String nowDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
//
//        //查询用户信息
//        try {
//            QueryWrapper userWrapper = new QueryWrapper();
//            userWrapper.eq("userName", loginusername);
//            BonusSysUser one = BonusSysUserService.getOne(userWrapper);
//            if (one == null) {
//                return Result.error(10002, "账号不存在");
//            }
//
//            if (one.getPassword().equals(loginpas)) {
//                String token = UUID.randomUUID().toString();//随机一个token
//
//                //覆盖之前的token
//                QueryWrapper<BonusToken> queryWrapperToken = new QueryWrapper<>();
//                queryWrapperToken.eq("userName", loginusername);
//                queryWrapperToken.ge("invalidTime", nowDate);//失效时间大于现在时间
//                List<BonusToken> tokens = BonusTokenService.list(queryWrapperToken);
//                if (tokens != null && tokens.size() > 0) {
//                    for (BonusToken deltoken : tokens) {
//                        deltoken.setInvalidtime(nowDate);//重新设置现在时间为失效时间
//                        BonusTokenService.updateById(deltoken);
//                    }
//                }
//
//                //生成新的token
//                BonusToken tokenSave = new BonusToken();
//                tokenSave.setUsername(one.getUsername());//系统账号
//                tokenSave.setToken(token);
//                tokenSave.setLogintime(nowDate);//设置登录时间
//                tokenSave.setInvalidtime(DateUtil.changeTime(nowDate, Calendar.HOUR_OF_DAY, 1));//设置失效时间
//                tokenSave.setCreateby(loginusername);//设置登录信息
//                tokenSave.setCreatedate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
//
//                //插入新的token数据
//                boolean re = BonusTokenService.save(tokenSave);
//                if (re) {
//                    return Result.OK("登录成功", new BonusloginVo(token, loginusername));
//                } else {
//                    return Result.error(10005, "出现token错误，登录失败，联系管理员");
//                }
//            } else {
//                return Result.error("密码错误");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Result.error(10004, "系统异常");
//        }
//
//
//    }

    @Autowired
    private ISysUserService sysUserService;//系统用户

    @Resource
    private BaseCommonService baseCommonService;//日志

    @Autowired
    private ISysDepartService sysDepartService;//部门

    @Autowired
    private ISysDictService sysDictService;//字典


    @ApiOperation(value = "登录接口1",notes = "账号密码登录--结果集如果登录成功（Success=true）,result中获取token和username,前端把token放进请求头，并用X-Access-Token命名")
    @AutoLog(value = "web端登录1")
    @PostMapping("/login")
    public Result<JSONObject> login(@RequestBody BonusSysUser sysLoginModel){
        Result<JSONObject> result = new Result<JSONObject>();
        String username = sysLoginModel.getUsername();
        String password = sysLoginModel.getPassword();
        log.info("sysLoginModel是："+sysLoginModel);
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername,username);
        SysUser sysUser = sysUserService.getOne(queryWrapper);
        log.info("sysUser是："+sysUser);
        result = sysUserService.checkUserIsEffective(sysUser);//校验用户是否有效
        if(!result.isSuccess()) {
            return result;
        }

        //2. 校验用户名或密码是否正确
        String userpassword = PasswordUtil.encrypt(username, password, sysUser.getSalt());
        String syspassword = sysUser.getPassword();
        if (!syspassword.equals(userpassword)) {
            result.error500("用户名或密码错误");
            return result;
        }

        // 生成token
        String token = JwtUtil.sign(username, syspassword);
        // 设置token缓存有效时间
        redisUtil.set(CommonConstant.PREFIX_USER_TOKEN + token, token);
        redisUtil.expire(CommonConstant.PREFIX_USER_TOKEN + token, JwtUtil.EXPIRE_TIME*2 / 1000);


        JSONObject jsonObject = new JSONObject();
        jsonObject.put("token",token);
        jsonObject.put("username",username);
        result.setCode(200);
        result.setMessage("登录成功");
        result.setResult(jsonObject);
        result.setSuccess(true);

        LoginUser loginUser = new LoginUser();
        BeanUtils.copyProperties(sysUser, loginUser);
        baseCommonService.addLog("用户名: " + username + ",登录成功！", CommonConstant.LOG_TYPE_1, null,loginUser);
        //update-end--Author:wangshuai  Date:20200714  for：登录日志没有记录人员
        return result;
    }


    @Autowired
    private ISysBaseAPI sysBaseAPI;//底层api，给其它模块使用

    //注销
    @PostMapping("/loginOut")
    @AutoLog(value = "注销")
    @ApiOperation(value = "注销接口", notes = "消除token--结果集为true或flase")
    Result loginOut(HttpServletRequest request) {
        String token = request.getHeader(CommonConstant.X_ACCESS_TOKEN);
        if(oConvertUtils.isEmpty(token)) {
            return Result.error("退出登录失败！");
        }
        String username = JwtUtil.getUsername(token);
        LoginUser sysUser = sysBaseAPI.getUserByName(username);
        if(sysUser!=null) {
            //update-begin--Author:wangshuai  Date:20200714  for：登出日志没有记录人员
            baseCommonService.addLog("用户名: "+sysUser.getRealname()+",退出成功！", CommonConstant.LOG_TYPE_1, null,sysUser);
            //update-end--Author:wangshuai  Date:20200714  for：登出日志没有记录人员
            log.info(" 用户名:  "+sysUser.getRealname()+",退出成功！ ");
            //清空用户登录Token缓存
            redisUtil.del(CommonConstant.PREFIX_USER_TOKEN + token);
            //清空用户登录Shiro权限缓存
            redisUtil.del(CommonConstant.PREFIX_USER_SHIRO_CACHE + sysUser.getId());
            //清空用户的缓存信息（包括部门信息），例如sys:cache:user::<username>
            redisUtil.del(String.format("%s::%s", CacheConstant.SYS_USERS_CACHE, sysUser.getUsername()));
            //调用shiro的logout
            SecurityUtils.getSubject().logout();
            return Result.ok("退出登录成功！");
        }else {
            return Result.error("Token无效!");
        }

    }


    /**
     * 本地：local minio：minio 阿里：alioss
     */
    @Value(value = "${jeecg.uploadType}")
    private String uploadType;

    //文件上传
    @PostMapping("/upFile")
    @AutoLog(value = "文件上传")
    @ApiOperation(value = "文件上传接口", notes = "上传excel文件，异步解析文件内容--结果集为true或flase,文件解析是否成功只能通过文件查询接口获取结果")
    Result upFile(HttpServletRequest request, HttpServletResponse response) {
        Result<?> result = new Result<>();
        String savePath = "";
        String bizPath = request.getParameter("biz");
        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        MultipartFile mf = multipartRequest.getFile("file");// 获取上传文件对象
        String orgName = mf.getOriginalFilename();//获取文件名称和后缀

        boolean excel = isExcel(orgName);
        if (excel == false) return Result.error("文件格式不对");



        if (oConvertUtils.isEmpty(bizPath)) {
            if (CommonConstant.UPLOAD_TYPE_OSS.equals(uploadType)) {
                bizPath = "upload";
            } else {
                bizPath = "";
            }
        }
        if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
            savePath = this.uploadLocal(mf, bizPath);
            log.info("走的本地上传");
        } else {
            savePath = CommonUtils.upload(mf, bizPath, uploadType);
            log.info("走的第三方上传");
        }
        if (oConvertUtils.isNotEmpty(savePath)) {
            result.setMessage(savePath);
            result.setSuccess(true);
        } else {
            result.setMessage("上传失败！");
            result.setSuccess(false);
        }
        return result;
    }


    //文件查询
    @AutoLog(value = "文件查询")
    @ApiOperation(value = "文件查询接口", notes = "(pageNo,pageSize,uptime,endtime,filename)时间格式按yyyy-MM-dd设定  -- 结果集为count,pageNo,fileList fileList为多个Bonus_sys_file")
    @PostMapping(value = "/getFileList")
    public Result<?> queryPageList(@RequestBody BonusPageVO bonusPageVO) {

        if (bonusPageVO == null) return Result.error("参数有误");
        Integer pageNo = bonusPageVO.getPageNo();
        Integer pageSize = bonusPageVO.getPageSize();
        Date uptime = bonusPageVO.getUptime();


        Date yesterday = null;//这是昨天
        Date endtime = null;
        try {
            Calendar c = Calendar.getInstance();

            c.setTime(uptime);
            c.add(Calendar.DAY_OF_MONTH, -1);
            yesterday = c.getTime();
            endtime = bonusPageVO.getEndtime();
        } catch (Exception e) {
            log.info("没有时间"+e.toString());

        }

        String filename = bonusPageVO.getFilename();


        if (pageNo == null && pageSize == null) return Result.error("pageNo&&pageSize有误");


        QueryWrapper<BonusSysFile> queryWrapper = new QueryWrapper<>();
        if (uptime != null && endtime != null) {//时间不为空
            queryWrapper.between("DATE_FORMAT(upTime,'%Y-%m-%d')", yesterday, endtime);
        }
        if (!StringUtils.isEmpty(filename)) {//文件名称不为空
            queryWrapper.like(StringUtils.isNotBlank(filename), "fileName", filename);
        }
        queryWrapper.orderByDesc("upTime");
        IPage<BonusSysFile> page = new Page<BonusSysFile>(pageNo, pageSize);

        page = BonusSysFileService.page(page, queryWrapper);
        List<BonusSysFile> list = page.getRecords();
        if (list.size() == 0 && list == null) return Result.error("没有文件");
        JSONObject data = new JSONObject();
        data.put("count", page.getTotal());
        data.put("pageNo", page.getCurrent());
        data.put("fileList", list);
//        QueryWrapper<BonusSysFile> queryWrapper = QueryGenerator.initQueryWrapper(bonusSysFile, req.getParameterMap());
//        Page<BonusSysFile> page = new Page<BonusSysFile>(pageNo, pageSize);
//        IPage<BonusSysFile> pageList = BonusSysFileService.page(page, queryWrapper);
        return Result.OK(data);
    }


    //-------------------------------------------------------------------------//
    //-------------------------------------------------------------------------//
    //---------------------------先暂时在jeecg基础上修改------------------------//
    //往激励金系统插入文件数据

    @Autowired
    IBonusSysFileService BonusSysFileService;

    private boolean bonusSaveFile(BonusSysFile sysFile) {
        return BonusSysFileService.save(sysFile);
    }

    @Autowired
    private RabbitMqClient rabbitMqClient;
    //-------------------------------------------------------------------------//
    //-------------------------------------------------------------------------//
    //-------------------------------------------------------------------------//


    /**
     * 本地文件上传
     *
     * @param mf      文件
     * @param bizPath 自定义路径
     * @return
     */
    private String uploadLocal(MultipartFile mf, String bizPath) {
        try {


            String ctxPath = uploadpath;
            String fileName = null;//文件名称
            File file = new File(ctxPath + File.separator + bizPath + File.separator);
            if (!file.exists()) {
                file.mkdirs();// 创建文件根目录
            }
            String orgName = mf.getOriginalFilename();//获取文件名称和后缀


            orgName = CommonUtils.getFileName(orgName);
            if (orgName.indexOf(".") != -1) {
                fileName = orgName.substring(0, orgName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + orgName.substring(orgName.lastIndexOf("."));
            } else {
                fileName = orgName + "_" + System.currentTimeMillis();
            }
            String savePath = file.getPath() + File.separator + fileName;


            //插入文件数据
            BonusSysFile sysFile = new BonusSysFile();
            sysFile.setFilename(orgName);
            String sysNo = "F" + System.nanoTime();
            sysFile.setFilesysno(sysNo);//返回的是纳秒
            sysFile.setFilepath(savePath);
            //当前接口时间
            sysFile.setUptime(new Date());
            boolean b = bonusSaveFile(sysFile);
            //异步
            //使用rokiitmq
            BaseMap map = new BaseMap();
            map.put("bonusFile", sysFile);//被发送的对象必须实现序列化
            ImportParams params = new ImportParams();
            params.setTitleRows(0);//从第二行
            params.setHeadRows(1);//从第一列
            params.setNeedSave(true);
            //解析文件成对象
            List<BonusDetail> list = ExcelImportUtil.importExcel(mf.getInputStream(), BonusDetail.class, params);
            System.out.println("111解析的对象为" + list);
            log.info("222解析的对象为" + list);
            map.put("list", list);
            map.put("sysNo", sysNo);
            rabbitMqClient.sendMessage("bonus", map);


            //-------------------------------------------------------------------------//
            //-------------------------------------------------------------------------//
            //-------------------------------------------------------------------------//


            File savefile = new File(savePath);


            FileCopyUtils.copy(mf.getBytes(), savefile);//文件写入本地

            String dbpath = null;
            if (oConvertUtils.isNotEmpty(bizPath)) {
                dbpath = bizPath + File.separator + fileName;
            } else {
                dbpath = fileName;
            }
            if (dbpath.contains("\\")) {
                dbpath = dbpath.replace("\\", "/");
            }
            return dbpath;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }


    //判断文件格式
    private boolean isExcel(String orgName) {
        String type = orgName.substring(orgName.lastIndexOf("."));
        if (type.equals(".xls") || type.equals(".xlsx")) {
            return true;
        } else {
            return false;
        }
    }


}
