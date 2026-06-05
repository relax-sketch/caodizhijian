# ZDB 数据结构与质检规则分析

## 1. 文件概述

- **文件格式**: SQLite 3.x 数据库（.zdb 为重命名后缀，未加密）
- **编码**: 数据库内部文本使用 GBK 编码存储
- **页大小**: 1024 bytes
- **SQLite版本**: 3.37.2
- **来源软件**: ForeStar（林调通/森调通）系列移动端采集软件
- **业务场景**: 草原综合监测数据采集与质检

## 2. 数据库表结构总览

### 2.1 表分类

| 类别 | 表名 | 说明 |
|------|------|------|
| **系统表** | SYS_MCParam | 系统参数 |
| | FL_SYS_TABLE | 表注册信息 |
| | FL_SYS_TABLEMETADATA | 字段元数据（别名、类型、控件等） |
| | FL_SYS_DATAREG | 数据注册 |
| | FL_SYS_GEODATASET | 空间数据集定义 |
| | FL_SYS_TABLERELATION | 表间关系定义 |
| | FL_SYS_ZYSJZD_CY | 数据字典（代码域值） |
| | FL_SYS_ZQSJZD2021_TJ | 行政区划字典 |
| | FL_SYS_BACKUPDICTS | 备份字典 |
| **业务主表** | YD_TRCY_PT | 天然草原样地（当年） |
| | YD_TRCY_PT2024 | 天然草原样地（上年对照） |
| | YD_RGCD_PT | 人工草地样地（当年） |
| | YD_RGCD_PT2024 | 人工草地样地（上年对照） |
| **业务子表** | YX_TRCY_TB | 样线表 |
| | YF_TRCYGC_TB | 观测小样方表 |
| | YF_TRCYCC_TB | 测产小样方表 |
| | YF_TRCY_TB | 高大草灌样方表 |
| | ZWDCB_GCYF_TB | 观测小样方植物调查表 |
| | ZWDCB_CCYF_TB | 测产小样方植物调查表 |
| | ZWDCB_GDCG_TB | 高大草灌植物调查表 |
| **附件表** | FS_DOCUMENT | 附件/照片记录 |
| **质检表** | FS_DATACHECK_ITEM | 质检规则定义（280条） |
| **修改追踪** | *_M 后缀表 | 各业务表的修改记录 |
| | FS_DELETE_RECORD | 删除记录 |
| | DATA_CHANGE_TB | 年度变化字段映射 |
| **空间索引** | idx_*_GEOMETRY* | R-Tree空间索引 |

### 2.2 表间关系（父子关联）

```
YD_TRCY_PT (天然草原样地)
├── YX_TRCY_TB (样线)          FK: MZGUID = XB_GLH
├── YF_TRCYGC_TB (观测小样方)   FK: MZGUID = XB_GLH
│   └── ZWDCB_GCYF_TB (观测植物调查)  FK: MZGUID = XB_GLH
├── YF_TRCYCC_TB (测产小样方)   FK: MZGUID = XB_GLH
│   └── ZWDCB_CCYF_TB (测产植物调查)  FK: MZGUID = XB_GLH
└── YF_TRCY_TB (高大草灌样方)   FK: MZGUID = XB_GLH
    └── ZWDCB_GDCG_TB (高大草灌植物调查) FK: MZGUID = XB_GLH

YD_RGCD_PT (人工草地样地) - 独立，无子表关联
```

关联机制：父表的 `MZGUID`（UUID）= 子表的 `XB_GLH`（小班关联号）

## 3. 核心业务表字段详解

### 3.1 YD_TRCY_PT — 天然草原样地表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| PK_UID | 主键 | INTEGER | 自增主键 |
| DC_RQ | 调查日期 | DATE | |
| DC_RY | 调查人 | VARCHAR(100) | |
| DC_ZT | 调查状态 | VARCHAR(2) | 0=未调查, 1=已调查 |
| YD_LX | 样地类型 | VARCHAR(2) | 11=固定样地-复测, 12=固定样地-改设, 21=其他样地 |
| SHENG | 省 | VARCHAR(2) | 行政区划代码 |
| SHI | 市 | VARCHAR(4) | |
| XIAN | 县 | VARCHAR(6) | |
| XIANG | 乡 | VARCHAR(9) | |
| CUN | 村 | VARCHAR(12) | |
| YD_ID | 样地编号 | VARCHAR(9) | 唯一标识，格式如 420105003 |
| MIAN_JI | 面积(公顷) | DOUBLE | |
| ZUOBIAO_E | 东经(°) | DOUBLE | WGS84经度 |
| ZUOBIAO_N | 北纬(°) | DOUBLE | WGS84纬度 |
| ZUOBIAO_X | GNSS坐标X | DOUBLE | CGS2000 6度分带(米) |
| ZUOBIAO_Y | GNSS坐标Y | DOUBLE | CGS2000 6度分带(米) |
| HAI_BA | 海拔 | DOUBLE | |
| LAND_TYPE | 地类 | VARCHAR(4) | 0401=天然牧草地, 0402=人工牧草地, 0403=其他草地 |
| VEGECOVER_TYPE | 植被覆盖类型 | VARCHAR(50) | 多级代码 |
| CD_L | 草地类 | VARCHAR(2) | 1~16等 |
| CD_XING | 草地型 | VARCHAR(100) | 三位数代码 |
| CD_XING_QT | 其他草地型 | VARCHAR(255) | 当CD_XING='826'时填写 |
| CD_LB | 草原起源 | VARCHAR(2) | 1=天然草原, 2=人工草地 |
| ZB_JG | 植被结构 | VARCHAR(1) | 1=草本型, 2=灌草型, 3=乔草型, 4=乔灌草型 |
| CDGD | 植被盖度(%) | DOUBLE | |
| ZBGD | 草群平均高度(cm) | DOUBLE | |
| XC_CL | 单位面积鲜草产量 | DOUBLE | |
| GC_CL | 单位面积干草产量 | DOUBLE | |
| KSMC_BL | 可食牧草比例(%) | DOUBLE | |
| DHC_BL | 毒害草比例(%) | DOUBLE | |
| YS_CZ | 优势草种 | VARCHAR(200) | 逗号分隔多个种名 |
| YS_DU | 优势度 | INTEGER | |
| LB_BL | 裸斑面积比例(%) | DOUBLE | 0-100 |
| LSFG_BL | 砾石覆盖面积比例(%) | DOUBLE | 0-100 |
| FS_HD | 覆沙厚度 | INTEGER | ≥0 |
| YJB_BL | 盐碱斑块面积比例(%) | DOUBLE | 0-100 |
| DB_QS_LX | 地表侵蚀类型 | VARCHAR(1) | 1=水力, 2=重力, 3=冰融, 4=风力, 5=无侵蚀 |
| DB_QS_CD | 地表侵蚀程度 | VARCHAR(1) | 0=无, 1=轻度, 2=中度, 3=重度 |
| GNLB | 功能类别 | VARCHAR(1) | 1=生态公益, 2=生产经营, 3=生活服务, 4=综合功能 |
| LYFS | 利用方式 | VARCHAR(100) | 多值代码 |
| STGN | 主要生态功能 | VARCHAR(2) | 1=水源涵养, 2=防风固沙, 3=水土保持, 4=其它 |
| LYQD | 利用强度 | VARCHAR(1) | 9=未利用, 1=轻度, 2=中度, 3=强度, 4=极度 |
| HQLM | 划区轮牧 | VARCHAR(1) | 1=是, 2=否 |
| JBCYQK | 基本草原 | VARCHAR(1) | 1=是, 2=否, 3=未划定 |
| DI_MAO | 地貌 | VARCHAR(1) | 1=极高山, 2=高山, 3=中山, 4=低山, 5=丘陵, 6=平原 |
| PO_DU | 坡度 | INTEGER | 0-90 |
| PO_WEI | 坡位 | VARCHAR(2) | 0=无坡, 1=坡顶, 2=坡上部, 3=坡中部, 4=坡下部, 5=坡脚 |
| PO_XIANG | 坡向 | VARCHAR(2) | 1=东, 2=南, 3=西, 4=北, 5=东南, 6=东北, 7=西南, 8=西北, 9=无坡向 |
| TU_RANG_ZD | 土壤质地 | VARCHAR(1) | 1=砂土, 2=砂壤地, 3=壤土, 4=粉砂壤土, 5=粘壤地, 6=壤粘地, 7=粘土 |
| TU_CENG_HD | 土层厚度 | INTEGER | ≥0 |
| MZGUID | 全局唯一ID | VARCHAR(100) | UUID，用于关联子表和照片 |
| ZXZ_ZP | 中心桩照片 | VARCHAR(100) | 照片路径 |
| YJ_ZP | 远景照 | VARCHAR(100) | |
| JJ_ZP | 近景照 | VARCHAR(100) | |
| TR_ZP | 土壤照片 | VARCHAR(100) | |
| GEOMETRY | 空间几何 | BLOB | 点坐标 |

### 3.2 YX_TRCY_TB — 样线表

每个样地需要 ≥3 条样线，样线长度 20m 或 40m。

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YX_ID | 样线编号 | INTEGER | 1,2,3... |
| YX_CD | 样线长度(米) | INTEGER | 20 或 40 |
| YX_GP | 是否改平 | VARCHAR(2) | 1=是(平距), 2=否(斜距) |
| YX_E | 终点经度(°) | DOUBLE | |
| YX_N | 终点纬度(°) | DOUBLE | |
| YX_X | 终点GNSS坐标X | DOUBLE | |
| YX_Y | 终点GNSS坐标Y | DOUBLE | |
| YX_FWJ | 方位角(°) | INTEGER | |
| YX_GD | 样线盖度(%) | DOUBLE | |
| YX_LBMJBL | 裸斑面积比例(%) | DOUBLE | |
| ZCJL_ZBFG_01~40 | 1~40号记录植被覆盖 | VARCHAR(2) | 1=有覆盖, 0=无覆盖 |
| ZCJL_LXLB_01~40 | 1~40号记录连续裸斑 | VARCHAR(2) | 0=非裸斑, 1=裸斑 |
| XB_GLH | 小班关联号 | VARCHAR(60) | 关联到父表YD_TRCY_PT.MZGUID |

说明：样线长度20m时记录1-20号，40m时记录1-40号。

### 3.3 YF_TRCYGC_TB — 观测小样方表

每个样地需要 ≥3 个观测小样方。

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | INTEGER | 1,2,3... |
| MIAN_JI | 面积(㎡) | VARCHAR(1) | 通常为 "4" |
| ZGD | 总盖度(%) | DOUBLE | 0-100 |
| CQPJ_GD | 草群平均高度(cm) | DOUBLE | ≥0 |
| ZWZS | 植物种数 | INTEGER | ≥0 |
| XB_GLH | 小班关联号 | VARCHAR(60) | 关联到父表 |

### 3.4 YF_TRCYCC_TB — 测产小样方表

每个样地需要 ≥3 个测产小样方。

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | INTEGER | |
| MIAN_JI | 面积(㎡) | VARCHAR(1) | 通常为 "1" |
| ZGD | 总盖度(%) | DOUBLE | 0-100 |
| CQPJ_GD | 草群平均高度(cm) | DOUBLE | |
| ZWZS | 植物种数 | INTEGER | |
| KLW_ZL | 枯落物总量(g) | DOUBLE | |
| HJ_XZ | 样方合计鲜重(g) | DOUBLE | |
| HJ_GZ | 样方合计干重(g) | DOUBLE | |
| KS_XZ | 样方可食鲜重(g) | DOUBLE | |
| KS_GZ | 样方可食干重(g) | DOUBLE | |
| DH_XZ | 样方毒害鲜重(g) | DOUBLE | |
| DH_GZ | 样方毒害干重(g) | DOUBLE | |
| XB_GLH | 小班关联号 | VARCHAR(60) | |

### 3.5 YF_TRCY_TB — 高大草灌样方表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | INTEGER | |
| MIAN_JI | 面积(㎡) | INTEGER | 通常为 100 |
| GDHJ_D_XZ | 大株合计鲜重(g) | DOUBLE | |
| GDHJ_D_GZ | 大株合计干重(g) | DOUBLE | |
| GDHJ_Z_XZ | 中株合计鲜重(g) | DOUBLE | |
| GDHJ_Z_GZ | 中株合计干重(g) | DOUBLE | |
| GDHJ_X_XZ | 小株合计鲜重(g) | DOUBLE | |
| GDHJ_X_GZ | 小株合计干重(g) | DOUBLE | |
| GDHJ_MJ | 合计覆盖面积(㎡) | DOUBLE | |
| GDHJ_ZS_XZ | 合计折算鲜重(g) | DOUBLE | |
| GDHJ_ZS_GZ | 合计折算干重(g) | DOUBLE | |
| XB_GLH | 小班关联号 | VARCHAR(60) | |

### 3.6 ZWDCB_GCYF_TB — 观测小样方植物调查表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | VARCHAR(11) | |
| XB_GLH | 样方关联字段 | VARCHAR(100) | 关联到YF_TRCYGC_TB.MZGUID |
| ZW_MC | 植物名称 | VARCHAR(200) | |
| H | 高度(cm) | DOUBLE | >0 |
| FVC | 盖度(%) | DOUBLE | 0-100 |
| KESHI | 是否可食 | VARCHAR(1) | 1=是, 2=否 |
| DUHAI | 是否毒害 | VARCHAR(1) | 1=是, 2=否 |
| YOUSHIZHONG | 是否为优势种 | VARCHAR(1) | 1=是, 2=否 |

### 3.7 ZWDCB_CCYF_TB — 测产小样方植物调查表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | VARCHAR(11) | |
| XB_GLH | 样方关联字段 | VARCHAR(100) | |
| ZW_MC | 植物名称 | VARCHAR(200) | |
| H | 高度(cm) | DOUBLE | ≥0 |
| FVC | 盖度(%) | DOUBLE | 0-100 |
| CCL_XIAN | 产草量鲜重(g/m²) | DOUBLE | >0 |
| CCL_GAN | 产草量干重(g/m²) | DOUBLE | |
| KESHI | 是否可食 | VARCHAR(1) | |
| DUHAI | 是否毒害 | VARCHAR(1) | |
| YOUSHIZHONG | 是否为优势种 | VARCHAR(1) | |

### 3.8 ZWDCB_GDCG_TB — 高大草灌植物调查表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| YD_ID | 样地编号 | VARCHAR(9) | |
| YF_ID | 样方号 | VARCHAR(11) | |
| XB_GLH | 样方关联字段 | VARCHAR(100) | |
| MC | 名称 | VARCHAR(50) | |
| ZC_LX | 株丛类型 | VARCHAR(2) | 1=大株丛, 2=中株丛, 3=小株丛 |
| ZS | 株丛数 | INTEGER | ≥0 |
| CJ | 丛径(cm) | INTEGER | ≥0 |
| GD | 株丛高度(cm) | INTEGER | ≥0 |
| XZ | 株丛鲜重(g/株) | INTEGER | ≥0 |
| GZ | 株丛干重(g/株) | INTEGER | |
| MJ | 覆盖面积(㎡) | DOUBLE | |
| ZS_XZ | 折算鲜重(g) | INTEGER | |
| ZS_GZ | 折算干重(g) | INTEGER | |

### 3.9 YD_RGCD_PT — 人工草地样地表

| 字段名 | 中文名 | 类型 | 说明 |
|--------|--------|------|------|
| SHENG~CUN | 行政区划 | VARCHAR | 同天然草原 |
| YD_ID | 样地编号 | VARCHAR(9) | |
| ZUOBIAO_E/N | 经纬度 | DOUBLE | |
| HAI_BA | 海拔 | INTEGER | |
| CZ_MC | 草种名称 | VARCHAR(20) | |
| CZ_ZHQ | 生命周期 | VARCHAR(10) | 1=一二年生, 2=多年生 |
| CZ_LY | 草种来源 | VARCHAR(10) | 1=国内, 2=国外 |
| GG_TJ | 灌溉条件 | VARCHAR(10) | 1=喷灌, 2=滴灌, 3=漫灌, 4=无 |
| XC_CHL | 全年鲜草产量 | DOUBLE | >0 |
| GC_CHL | 全年干草产量 | DOUBLE | >0 |
| ZZ_NF | 种植年份 | VARCHAR(4) | |
| DC_RY | 调查人员 | VARCHAR(200) | |
| DC_RQ | 调查日期 | DATE | |
| DC_ZT | 调查状态 | VARCHAR(2) | |
| YD_LX | 样地类型 | VARCHAR(2) | |
| MIAN_JI | 面积 | DOUBLE | |

## 4. 质检规则体系

### 4.1 规则存储结构

质检规则存储在 `FS_DATACHECK_ITEM` 表中，共 280 条，采用树形结构：
- **根节点** (i_pobjectid=NULL): 顶级分类（属性检查、空间检查）
- **分组节点** (i_checkitemgrouptype=0): 按表分组（天然草原样地、样线、观测小样方等）
- **规则节点** (i_checkitemgrouptype=1, i_isapply=1): 实际执行的检查规则

### 4.2 规则执行逻辑

每条规则的 `b_datacheckparameter` 字段为 JSON，核心字段：

```json
{
  "Type": "ForeStar.DataCheck.AttributeCheck.LogicRelationCheckItem,ForeStar.DataCheck",
  "LogicCondition": "SQL WHERE条件 — 匹配到的记录即为错误数据",
  "LogicResult": "1<>1",
  "MetadataDataSource": "目标表名",
  "MetadataFields": "定位字段（用于展示错误位置）"
}
```

**执行方式**: `SELECT {MetadataFields} FROM {MetadataDataSource} WHERE {LogicCondition}`
- 查询结果非空 = 存在质检错误
- `MetadataFields` 用于定位具体哪条记录出错

### 4.3 规则分组与详细清单

#### 4.3.1 天然草原样地 (YD_TRCY_PT) — 46条规则

| 规则名称 | SQL条件 | 定位字段 |
|----------|---------|----------|
| 调查人员为空 | `DC_RY IS NULL OR DC_RY='' OR DC_RY=' '` | XIAN,XIANG,CUN,YD_ID |
| 调查日期为空 | `DC_RQ IS NULL` | XIAN,XIANG,CUN,YD_ID |
| 调查状态为空 | `DC_ZT IS NULL OR DC_ZT='' OR DC_ZT=' '` | XIAN,XIANG,CUN,YD_ID |
| 样地类型为空 | `YD_LX IS NULL OR YD_LX='' OR YD_LX=' '` | XIAN,XIANG,CUN,YD_ID |
| 省为空 | `SHENG IS NULL OR SHENG='' OR SHENG=' '` | XIAN,XIANG,YD_ID |
| 市为空 | `SHI IS NULL OR SHI='' OR SHI=' '` | XIAN,XIANG,CUN,YD_ID |
| 县为空 | `XIAN IS NULL OR XIAN='' OR XIAN=' '` | XIAN,XIANG,CUN,YD_ID |
| 样地号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` | XIAN,XIANG,CUN,YD_ID |
| GNSS坐标X为空或整数位<8 | `LENGTH(ROUND(ZUOBIAO_X,0))<8 OR ZUOBIAO_X IS NULL OR ZUOBIAO_X=0 OR TRIM(ZUOBIAO_X)=''` | XIAN,XIANG,CUN,YD_ID |
| GNSS坐标Y为空或整数位<7 | `LENGTH(ROUND(ZUOBIAO_X,0))<7 OR ZUOBIAO_Y IS NULL OR ZUOBIAO_Y=0 OR TRIM(ZUOBIAO_Y)=''` | XIAN,XIANG,CUN,YD_ID |
| 海拔为空 | `HAI_BA IS NULL OR HAI_BA=0 OR TRIM(HAI_BA)=''` | XIAN,XIANG,CUN,YD_ID |
| 草地类为空 | `CD_L IS NULL OR CD_L='' OR CD_L=' '` | XIAN,XIANG,CUN,YD_ID |
| 草地型为空 | `CD_XING IS NULL OR CD_XING='' OR CD_XING=' '` | XIAN,XIANG,CUN,YD_ID |
| 草地型为其他时，其他草地型为空 | `CD_XING='826' AND (CD_XING_QT IS NULL OR CD_XING_QT='' OR CD_XING_QT=' ')` | XIAN,XIANG,CUN,YD_ID |
| 草地起源为空 | `CD_LB IS NULL OR CD_LB='' OR CD_LB='? '` | XIAN,XIANG,CUN,YD_ID |
| 植被结构为空 | `ZB_JG IS NULL OR ZB_JG='' OR ZB_JG=' '` | XIAN,XIANG,CUN,YD_ID |
| 优势草种为空 | `YS_CZ IS NULL OR YS_CZ='' OR YS_CZ=' '` | XIAN,XIANG,CUN,YD_ID |
| 砾石覆盖面积比例值应在0-100 | `LSFG_BL<0 OR LSFG_BL>100 OR LSFG_BL IS NULL OR TRIM(LSFG_BL)=''` | XIAN,XIANG,CUN,YD_ID |
| 覆沙厚度为空 | `FS_HD IS NULL OR FS_HD<0 OR TRIM(FS_HD)=''` | XIAN,XIANG,CUN,YD_ID |
| 盐碱斑块面积比例应在0-100 | `YJB_BL<0 OR YJB_BL>100 OR YJB_BL IS NULL OR TRIM(YJB_BL)=''` | XIAN,XIANG,CUN,YD_ID |
| 地表侵蚀类型为空 | `DB_QS_LX IS NULL OR DB_QS_LX='' OR DB_QS_LX=' '` | XIAN,XIANG,CUN,YD_ID |
| 侵蚀类型不为无侵蚀时，侵蚀程度为空 | `DB_QS_LX!='5' AND (DB_QS_CD IS NULL OR DB_QS_CD='' OR DB_QS_CD=' ')` | XIAN,XIANG,CUN,YD_ID |
| 功能类别为空 | `GNLB IS NULL OR GNLB='' OR GNLB=' '` | XIAN,XIANG,CUN,YD_ID |
| 利用方式为空 | `(LYFS IS NULL OR LYFS='' OR LYFS=' ') AND (GNLB IN ('2','3','4') OR GNLB IS NULL OR GNLB='' OR GNLB=' ')` | XIAN,XIANG,CUN,YD_ID |
| 主要生态功能为空 | `(GNLB='1' OR GNLB IS NULL OR GNLB='' OR GNLB=' ') AND (STGN IS NULL OR STGN='' OR STGN=' ')` | XIAN,XIANG,CUN,YD_ID |
| 利用强度为空 | `LYQD IS NULL OR LYQD='' OR LYQD=' '` | XIAN,XIANG,CUN,YD_ID |
| 划区轮牧为空 | `HQLM IS NULL OR HQLM='' OR HQLM=' '` | XIAN,XIANG,CUN,YD_ID |
| 基本草原为空 | `JBCYQK IS NULL OR JBCYQK='' OR JBCYQK=' '` | XIAN,XIANG,CUN,YD_ID |
| 地貌为空 | `DI_MAO IS NULL OR DI_MAO='' OR DI_MAO=' '` | XIAN,XIANG,CUN,YD_ID |
| 坡度应在0-90 | `PO_DU IS NULL OR PO_DU<0 OR PO_DU>90 OR TRIM(PO_DU)=''` | XIAN,XIANG,CUN,YD_ID |
| 坡位为空 | `PO_WEI IS NULL OR PO_WEI='' OR PO_WEI=' '` | XIAN,XIANG,CUN,YD_ID |
| 坡位不为无坡时，坡向为空 | `(PO_XIANG IS NULL OR PO_XIANG='' OR PO_XIANG=' ') AND PO_WEI!='0'` | XIAN,XIANG,CUN,YD_ID |
| 土壤质地为空 | `TU_RANG_ZD IS NULL OR TU_RANG_ZD='' OR TU_RANG_ZD=' '` | XIAN,XIANG,CUN,YD_ID |
| 土层厚度为空 | `TU_CENG_HD IS NULL OR TU_CENG_HD<0 OR TRIM(TU_CENG_HD)=''` | XIAN,XIANG,CUN,YD_ID |
| 中心桩照片未拍摄 | `ZXZ_ZP IS NULL OR ZXZ_ZP='' OR ZXZ_ZP=' '` | XIAN,XIANG,CUN,YD_ID |
| 远景照未拍摄 | `YJ_ZP IS NULL OR YJ_ZP='' OR YJ_ZP=' '` | XIAN,XIANG,CUN,YD_ID |
| 近景照未拍摄 | `JJ_ZP IS NULL OR JJ_ZP='' OR JJ_ZP=' '` | XIAN,XIANG,CUN,YD_ID |
| 土壤照片未拍摄 | `TR_ZP IS NULL OR TR_ZP='' OR TR_ZP=' '` | XIAN,XIANG,CUN,YD_ID |
| 植被覆盖类型为空 | `VEGECOVER_TYPE IS NULL OR VEGECOVER_TYPE='' OR VEGECOVER_TYPE=' '` | XIAN,XIANG,CUN,YD_ID |
| 地类为空 | `LAND_TYPE IS NULL OR LAND_TYPE='' OR LAND_TYPE=' '` | XIAN,XIANG,CUN,YD_ID |
| 裸斑面积比例为空 | `LB_BL<0 OR LB_BL>100 OR LB_BL IS NULL OR TRIM(LB_BL)=''` | XIAN,XIANG,CUN,YD_ID |
| **跨表关联检查** | | |
| 缺少观测样方表 | `MZGUID NOT IN (SELECT XB_GLH FROM YF_TRCYGC_TB WHERE XB_GLH IS NOT NULL)` | XIAN,XIANG,CUN,YD_ID |
| 缺少测产样方表 | `MZGUID NOT IN (SELECT XB_GLH FROM YF_TRCYCC_TB WHERE XB_GLH IS NOT NULL)` | XIAN,XIANG,CUN,YD_ID |
| 缺少样线表 | `MZGUID NOT IN (SELECT XB_GLH FROM YX_TRCY_TB WHERE XB_GLH IS NOT NULL)` | XIAN,XIANG,CUN,YD_ID |
| 样线不足3条 | `MZGUID IN(SELECT D.MZGUID FROM (SELECT A.MZGUID,C.YXSL FROM YD_TRCY_PT A LEFT JOIN (SELECT B.XB_GLH,COUNT(B.XB_GLH) AS YXSL FROM YX_TRCY_TB B GROUP BY B.XB_GLH) C ON A.MZGUID=C.XB_GLH) D WHERE D.YXSL<3)` | XIAN,XIANG,CUN,YD_ID |
| 观测样方不足3个 | `MZGUID IN(SELECT D.MZGUID FROM (SELECT A.MZGUID,C.YXSL FROM YD_TRCY_PT A LEFT JOIN (SELECT B.XB_GLH,COUNT(B.XB_GLH) AS YXSL FROM YF_TRCYGC_TB B GROUP BY B.XB_GLH) C ON A.MZGUID=C.XB_GLH) D WHERE D.YXSL<3)` | XIAN,XIANG,CUN,YD_ID |
| 测产样方不足3个 | `MZGUID IN(SELECT D.MZGUID FROM (SELECT A.MZGUID,C.YXSL FROM YD_TRCY_PT A LEFT JOIN (SELECT B.XB_GLH,COUNT(B.XB_GLH) AS YXSL FROM YF_TRCYCC_TB B GROUP BY B.XB_GLH) C ON A.MZGUID=C.XB_GLH) D WHERE D.YXSL<3)` | XIAN,XIANG,CUN,YD_ID |
| 样线长度不一致 | `MZGUID IN (SELECT B.XB_GLH FROM (SELECT A.XB_GLH,A.YX_CD FROM YX_TRCY_TB A GROUP BY A.XB_GLH,A.YX_CD) B GROUP BY B.XB_GLH HAVING COUNT(*)>1)` | XIAN,XIANG,CUN,YD_ID |

#### 4.3.2 样线表 (YX_TRCY_TB) — 86条规则

| 规则名称 | SQL条件 | 定位字段 |
|----------|---------|----------|
| 样地号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` | YD_ID,YX_ID |
| 样线编号为空 | `YX_ID IS NULL OR TRIM(YX_ID)=''` | YD_ID,YX_ID |
| 样线长度为空 | `YX_CD IS NULL OR YX_CD=0` | YD_ID,YX_ID |
| 是否改平为空 | `YX_GP IS NULL OR YX_GP='' OR YX_GP=' '` | YD_ID,YX_ID |
| 终点GNSS坐标X为空 | `YX_X IS NULL OR YX_X='' OR TRIM(YX_X)=' '` | YD_ID,YX_ID |
| 终点GNSS坐标Y为空 | `YX_Y IS NULL OR YX_Y='' OR TRIM(YX_Y)=' '` | YD_ID,YX_ID |
| 1~20号记录植被覆盖为空 | `ZCJL_ZBFG_XX IS NULL OR ZCJL_ZBFG_XX='' OR ZCJL_ZBFG_XX=' '` | YD_ID,YX_ID |
| 21~40号记录植被覆盖为空(仅40m) | `YX_CD=40 AND (ZCJL_ZBFG_XX IS NULL OR ...)` | YD_ID,YX_ID |
| 1~20号记录连续裸斑为空 | `ZCJL_LXLB_XX IS NULL OR ...` | YD_ID,YX_ID |
| 21~40号记录连续裸斑为空(仅40m) | `YX_CD=40 AND (ZCJL_LXLB_XX IS NULL OR ...)` | YD_ID,YX_ID |

#### 4.3.3 观测小样方 (YF_TRCYGC_TB) — 7条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR TRIM(YF_ID)=''` |
| 面积为空 | `MIAN_JI IS NULL OR MIAN_JI=0 OR TRIM(MIAN_JI)=''` |
| 总盖度取值[0,100] | `ZGD IS NULL OR TRIM(ZGD)='' OR ZGD<0 OR ZGD>100` |
| 草群平均高度为空 | `CQPJ_GD IS NULL OR TRIM(CQPJ_GD)='' OR CQPJ_GD<0` |
| 植物种数为空 | `ZWZS IS NULL OR TRIM(ZWZS)='' OR ZWZS<0` |
| 总盖度不为0时，必须填写植物调查表 | `ZGD!=0 AND MZGUID NOT IN (SELECT XB_GLH FROM ZWDCB_GCYF_TB WHERE XB_GLH IS NOT NULL)` |

#### 4.3.4 观测小样方植物调查 (ZWDCB_GCYF_TB) — 7条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR YF_ID='' OR YF_ID=' '` |
| 植物名称为空 | `ZW_MC IS NULL OR ZW_MC='' OR ZW_MC=' '` |
| 高度为空 | `H IS NULL OR TRIM(H)='' OR H<=0` |
| 盖度取值[0,100] | `FVC IS NULL OR TRIM(FVC)='' OR FVC<0 OR FVC>100` |
| 是否可食为空 | `KESHI IS NULL OR KESHI='' OR KESHI=' '` |
| 是否为优势种为空 | `YOUSHIZHONG IS NULL OR YOUSHIZHONG='' OR YOUSHIZHONG=' '` |

#### 4.3.5 测产小样方 (YF_TRCYCC_TB) — 7条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR TRIM(YF_ID)=''` |
| 总盖度取值[0,100] | `ZGD IS NULL OR TRIM(ZGD)='' OR ZGD<0 OR ZGD>100` |
| 草群平均高度为空 | `CQPJ_GD IS NULL OR TRIM(CQPJ_GD)='' OR CQPJ_GD<0` |
| 植物种数为空 | `ZWZS IS NULL OR TRIM(ZWZS)=''` |
| 枯落物总量为空 | `KLW_ZL IS NULL OR TRIM(KLW_ZL)=''` |
| 总盖度不为0时，必须填写植物调查表 | `ZGD!=0 AND MZGUID NOT IN (SELECT XB_GLH FROM ZWDCB_CCYF_TB WHERE XB_GLH IS NOT NULL)` |

#### 4.3.6 测产小样方植物调查 (ZWDCB_CCYF_TB) — 9条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR YF_ID='' OR YF_ID=' '` |
| 植物名称为空 | `ZW_MC IS NULL OR ZW_MC='' OR ZW_MC=' '` |
| 高度为空 | `H IS NULL OR TRIM(H)='' OR H<0` |
| 盖度取值[0,100] | `FVC IS NULL OR TRIM(FVC)='' OR FVC<0 OR FVC>100` |
| 产草量鲜重为空 | `CCL_XIAN IS NULL OR TRIM(CCL_XIAN)='' OR CCL_XIAN<=0` |
| 是否可食为空 | `KESHI IS NULL OR KESHI='' OR KESHI=' '` |
| 是否毒害为空 | `DUHAI IS NULL OR DUHAI='' OR DUHAI=' '` |
| 是否为优势种为空 | `YOUSHIZHONG IS NULL OR YOUSHIZHONG='' OR YOUSHIZHONG=' '` |

#### 4.3.7 高大草灌样方 (YF_TRCY_TB) — 4条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR TRIM(YF_ID)=''` |
| 面积为空 | `MIAN_JI IS NULL OR TRIM(MIAN_JI)='' OR MIAN_JI=0` |
| 缺少高大草灌植物调查表 | `MZGUID NOT IN (SELECT XB_GLH FROM ZWDCB_GDCG_TB WHERE XB_GLH IS NOT NULL)` |

#### 4.3.8 高大草灌植物调查 (ZWDCB_GDCG_TB) — 8条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 样地编号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 样方号为空 | `YF_ID IS NULL OR YF_ID='' OR YF_ID=' '` |
| 名称为空 | `MC IS NULL OR MC='' OR MC=' '` |
| 株丛数为空 | `ZS IS NULL OR ZS<0 OR TRIM(ZS)=''` |
| 丛径为空 | `CJ IS NULL OR CJ<0 OR TRIM(CJ)=''` |
| 株丛高度为空 | `GD IS NULL OR GD<0 OR TRIM(GD)=''` |
| 株丛鲜重为空 | `XZ IS NULL OR XZ<0 OR TRIM(XZ)=''` |
| 株丛类型为空 | `ZC_LX IS NULL OR ZC_LX='' OR ZC_LX=' '` |

#### 4.3.9 人工草地样地 (YD_RGCD_PT) — 19条规则

| 规则名称 | SQL条件 |
|----------|---------|
| 省为空 | `SHENG IS NULL OR SHENG='' OR SHENG=' '` |
| 市为空 | `SHI IS NULL OR SHI='' OR SHI=' '` |
| 县为空 | `XIAN IS NULL OR XIAN='' OR XIAN=' '` |
| 乡为空 | `XIANG IS NULL OR XIANG='' OR XIANG=' '` |
| 样地号为空 | `YD_ID IS NULL OR YD_ID='' OR YD_ID=' '` |
| 海拔为空或<-100 | `HAI_BA IS NULL OR TRIM(HAI_BA)='' OR HAI_BA<-100` |
| 草种名称为空 | `CZ_MC IS NULL OR CZ_MC='' OR CZ_MC=' '` |
| 生命周期为空 | `CZ_ZHQ IS NULL OR CZ_ZHQ='' OR CZ_ZHQ=' '` |
| 草种来源为空 | `CZ_LY IS NULL OR CZ_LY='' OR CZ_LY=' '` |
| 灌溉条件为空 | `GG_TJ IS NULL OR GG_TJ='' OR GG_TJ=' '` |
| 全年鲜草产量为空 | `XC_CHL IS NULL OR XC_CHL='' OR XC_CHL<=0` |
| 全年干草产量为空 | `GC_CHL IS NULL OR GC_CHL='' OR GC_CHL<=0` |
| 种植年份为空 | `ZZ_NF IS NULL OR ZZ_NF='' OR ZZ_NF=' '` |
| 调查人员为空 | `DC_RY IS NULL OR DC_RY='' OR DC_RY=' '` |
| 调查日期为空 | `DC_RQ IS NULL OR DC_RQ='' OR DC_RQ=' '` |
| 调查状态为空 | `DC_ZT IS NULL OR DC_ZT='' OR DC_ZT=' '` |
| 样地类型为空 | `YD_LX IS NULL OR YD_LX='' OR YD_LX=' '` |
| 样地面积为空 | `MIAN_JI IS NULL OR MIAN_JI=0 OR TRIM(MIAN_JI)=''` |

#### 4.3.10 荒漠调查区特殊规则

| 规则名称 | SQL条件 |
|----------|---------|
| 石漠化调查区时，基岩裸露度为空或不在1%-100% | `HMDCQLX='4' AND (ROCK_EXPOSURE IS NULL OR ROCK_EXPOSURE<1 OR ROCK_EXPOSURE>100 OR TRIM(ROCK_EXPOSURE)='')` |

### 4.4 当前附加规则增改说明（2026-06-04）

以下规则已在 `app/src/main/assets/rules/rule-set.json` 中生成；后续规则维护请修改 `rules-src/`，然后运行：

```bash
python tools/validate_rules.py
python tools/build_rules.py
```

| 规则ID | 类型 | 规则说明 |
|---|---|---|
| ADD_GRASS_038 | 强制 | 样线照片要求已调整为每条样线至少 2 张，少于 2 张时报错。 |
| ADD_GRASS_039 | 强制 | 样线终点到样地中心桩距离应为 20m，19.5m 至 20.5m 视为合格；原 60m 规则已修正。 |
| ADD_GRASS_046 | 强制 | 测产样方草群平均高应相对植物高度普通平均值偏向覆盖度更高的植物高度一方；`H/FVC` 从植物调查分表读取，不再作为样方表必需字段导致规则无法运行。 |
| ADD_GRASS_047 | 强制 | 测产样方总盖度不应大于植物调查分表盖度值之和；原“介于植物最小/最大盖度之间”的判断已修正。 |
| ADD_GRASS_060 | 强制 | 观测样方草群平均高应相对植物高度普通平均值偏向覆盖度更高的植物高度一方；`H/FVC` 从植物调查分表读取，不再作为样方表必需字段导致规则无法运行。 |
| ADD_GRASS_061 | 强制 | 观测样方总盖度不应大于植物调查分表盖度值之和；原“介于植物最小/最大盖度之间”的判断已修正。 |
| ADD_GRASS_050 | 强制 | 同一测产样方内植物分类最多为四类：优势可食、优势毒害、其他可食、其他毒害；同一样方内分类不可重复。 |
| ADD_GRASS_064 | 强制 | 同一观测样方内植物分类最多为四类：优势可食、优势毒害、其他可食、其他毒害；同一样方内分类不可重复。 |
| ADD_GRASS_070 | 提示 | 测产样方主要推荐高度按 `SUM(FVC / 100 * H)` 计算，`FVC=30` 按 30% 即 0.3 参与计算；与草群平均高偏差超过 0.5cm 时提示核对；`H/FVC` 从植物调查分表读取。 |
| ADD_GRASS_071 | 提示 | 观测样方主要推荐高度按 `SUM(FVC / 100 * H)` 计算，`FVC=30` 按 30% 即 0.3 参与计算；与草群平均高偏差超过 0.5cm 时提示核对；`H/FVC` 从植物调查分表读取。 |

## 5. 数据字典（代码域值）

数据字典存储在 `FL_SYS_ZYSJZD_CY` 表中，通过 `c_domainname` 字段区分不同域。

### 5.1 关键域值对照

| 域名 | 字段 | 代码值 |
|------|------|--------|
| 调查状态 | DC_ZT | 0=未调查, 1=已调查 |
| 样地类型 | YD_LX | 11=固定样地-复测, 12=固定样地-改设, 21=其他样地 |
| 草地类 | CD_L | 1=温性草甸草原, 2=温性草原, 3=温性荒漠草原, 4=高寒草甸草原, 5=高寒草原, 6=高寒荒漠草原, 7=高寒草甸, 8=低地草甸, 9=山地草甸, 11=温性荒漠, 12=温性草原化荒漠, 13=高寒荒漠, 14=暖性草丛, 15=暖性灌草丛, 16=其他 |
| 草原起源 | CD_LB | 1=天然草原, 2=人工草地 |
| 植被结构 | ZB_JG | 1=草本型, 2=灌草型, 3=乔草型, 4=乔灌草型 |
| 地表侵蚀类型 | DB_QS_LX | 1=水力, 2=重力, 3=冰融, 4=风力, 5=无侵蚀 |
| 地表侵蚀程度 | DB_QS_CD | 0=无, 1=轻度, 2=中度, 3=重度 |
| 功能类别 | GNLB | 1=生态公益, 2=生产经营, 3=生活服务, 4=综合功能 |
| 利用强度 | LYQD | 9=未利用, 1=轻度, 2=中度, 3=强度, 4=极度 |
| 划区轮牧 | HQLM | 1=是, 2=否 |
| 基本草原 | JBCYQK | 1=是, 2=否, 3=未划定 |
| 地貌 | DI_MAO | 1=极高山, 2=高山, 3=中山, 4=低山, 5=丘陵, 6=平原 |
| 坡位 | PO_WEI | 0=无坡, 1=坡顶, 2=坡上部, 3=坡中部, 4=坡下部, 5=坡脚 |
| 坡向 | PO_XIANG | 1=东, 2=南, 3=西, 4=北, 5=东南, 6=东北, 7=西南, 8=西北, 9=无坡向 |
| 土壤质地 | TU_RANG_ZD | 1=砂土, 2=砂壤地, 3=壤土, 4=粉砂壤土, 5=粘壤地, 6=壤粘地, 7=粘土 |
| 是否改平 | YX_GP | 1=是(平距), 2=否(斜距) |
| 株丛类型 | ZC_LX | 1=大株丛, 2=中株丛, 3=小株丛 |
| 是否 | KESHI/DUHAI/YOUSHIZHONG | 1=是, 2=否 |
| 地类 | LAND_TYPE | 0401=天然牧草地, 0402=人工牧草地, 0403=其他草地 |
| 生命周期 | CZ_ZHQ | 1=一二年生, 2=多年生 |
| 草种来源 | CZ_LY | 1=国内, 2=国外 |
| 灌溉条件 | GG_TJ | 1=喷灌, 2=滴灌, 3=漫灌, 4=无 |
| 利用方式 | LYFS | 11=全年放牧, 12=冬季放牧, 13=春季放牧, 14=夏季放牧, 15=夏秋放牧, 16=秋冬放牧, 20=打(剪)草, 31=自然保护, 32=景观绿化, 33=科研实验, 34=水源涵养, 35=固土固沙, 36=其他 |
| 荒漠调查区类型 | HMDCQLX | 1=荒漠化沙化调查区, 2=荒漠化调查区, 3=沙化调查区, 4=石漠化调查区, 5=石漠化沙化调查区 |

## 6. Android App 开发要点

### 6.1 文件访问

- 路径格式: `内部共享存储空间/草原监测/数据/{项目名}/{项目名}.zdb`
- `数据` 目录下可能有多个项目文件夹
- 用户手动选择到 `数据` 目录即可，App 自动扫描子文件夹中的 .zdb 文件
- 需要 Android SAF (Storage Access Framework) 或 MANAGE_EXTERNAL_STORAGE 权限

### 6.2 数据库读取

- 使用 Android SQLite API 直接打开 .zdb 文件（只读模式）
- 文本编码为 GBK，Android 的 SQLite 默认 UTF-8，需注意：
  - `FL_SYS_TABLEMETADATA` 中的中文别名是 GBK 编码
  - `FS_DATACHECK_ITEM` 中的规则名称是 GBK 编码
  - **业务数据本身（如 YS_CZ 优势草种）是 UTF-8**（从JSON样本数据可见）
  - 建议：对系统表字段做 GBK→UTF-8 转码，业务数据直接读取

### 6.3 质检执行策略

1. **从 FS_DATACHECK_ITEM 读取规则**（i_isapply=1 的有效规则）
2. **解析 b_datacheckparameter JSON**，提取 MetadataDataSource、LogicCondition、MetadataFields
3. **构造查询**: `SELECT {MetadataFields} FROM {MetadataDataSource} WHERE {LogicCondition}`
4. **执行查询**，有结果即为错误
5. **展示错误**: 按规则分组，显示错误记录的定位信息（县+乡+村+样地号）
6. **不修改 zdb 文件**，仅做只读质检

### 6.4 自定义规则扩展

App 需要支持用户添加自定义 SQL 质检规则：
- 规则格式与现有一致：目标表 + WHERE条件 + 定位字段
- 自定义规则存储在 App 本地数据库中（不写入 zdb）
- 可以导入/导出规则配置

### 6.5 注意事项

- zdb 中的数据表可能为空（模板库），也可能有数据（实际采集后的库）
- 同一个 zdb 可能包含也可能不包含某些表（如加密样地相关表 YD_JM_PT 在本样本中不存在）
- 质检规则中引用的表如果不存在，应跳过该规则而非报错
- `*2024` 后缀表为上年对照数据，质检主要针对当年表
- `*_M` 后缀表为修改追踪表，不参与质检
- 样线记录数与样线长度相关：20m=20条记录，40m=40条记录

## 7. 数据样本（来自JSON爬取数据）

### 7.1 天然草原样地示例

```json
{
  "YD_ID": "420105003",
  "SHENG": "42", "SHI": "4201", "XIAN": "420105",
  "XIANG": "420105010", "CUN": "420105010018",
  "ZUOBIAO_E": 114.1469888, "ZUOBIAO_N": 30.5506828,
  "ZUOBIAO_X": 20226201.1, "ZUOBIAO_Y": 3384627.3,
  "HAI_BA": 6.9,
  "CD_L": "16", "CD_XING": "598", "CD_LB": "1",
  "ZB_JG": "1", "CDGD": 95.0, "ZBGD": 78.33,
  "YS_CZ": "狗牙根，野胡萝卜，加拿大一枝黄花",
  "GNLB": "4", "LYFS": "36", "LYQD": "9",
  "DI_MAO": "6", "PO_DU": 0, "PO_WEI": "0",
  "TU_RANG_ZD": "2", "TU_CENG_HD": 40
}
```

### 7.2 样线示例

```json
{
  "YD_ID": "421381036", "YX_ID": 3, "YX_CD": 20,
  "YX_GP": "1", "YX_GD": 100.0, "YX_LBMJBL": 0.0,
  "ZCJL_ZBFG_01": "1", "ZCJL_LXLB_01": "0",
  "ZCJL_ZBFG_20": "1", "ZCJL_LXLB_20": "0",
  "ZCJL_ZBFG_21": "", "ZCJL_LXLB_21": ""
}
```
注意：20m样线只填1-20号，21-40号为空。

### 7.3 测产小样方示例

```json
{
  "YD_ID": "421381036", "YF_ID": 1, "MIAN_JI": "1",
  "ZGD": 85.0, "CQPJ_GD": 30.0, "ZWZS": 8,
  "KLW_ZL": 45,
  "HJ_XZ": 385.0, "HJ_GZ": 147.0,
  "KS_XZ": 385.0, "KS_GZ": 147.0,
  "DH_XZ": 0.0, "DH_GZ": 0.0
}
```
