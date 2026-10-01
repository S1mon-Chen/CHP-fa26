// Ant Design 极简主义主题配置
import { theme } from 'antd';

const { defaultAlgorithm, defaultSeed } = theme;

const minimalTheme = {
  algorithm: defaultAlgorithm,
  token: {
    // 配色方案 - 极简主义
    colorPrimary: '#333333',      // 主色调 - 深灰色，专业感
    colorBgLayout: '#ffffff',     // 布局背景 - 纯白
    colorBgContainer: '#fafafa',  // 容器背景 - 浅灰色
    colorText: '#333333',         // 文本颜色 - 深灰色
    colorTextSecondary: '#666666', // 次要文本 - 中灰色
    colorBorder: '#e5e5e5',       // 边框颜色 - 浅灰色
    
    // 字体配置
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    fontSize: 14,
    
    // 圆角配置 - 适度圆角，体现极简风格
    borderRadius: 4,
    borderRadiusSM: 2,
    borderRadiusLG: 6,
    
    // 阴影配置 - 轻微阴影，增加层次感
    boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
    boxShadowHover: '0 2px 8px rgba(0, 0, 0, 0.08)',
    
    // 间距配置
    marginXS: 4,
    marginSM: 8,
    marginMD: 16,
    marginLG: 24,
    marginXL: 32,
    
    // 组件尺寸
    sizeXS: 24,
    sizeSM: 28,
    sizeMD: 32,
    sizeLG: 36,
    sizeXL: 40,
  },
  components: {
    // 按钮配置
    Button: {
      colorPrimary: '#333333',
      colorPrimaryHover: '#555555',
      colorPrimaryActive: '#222222',
      borderRadius: 4,
      paddingXS: '4px 8px',
      paddingSM: '6px 12px',
      paddingMD: '8px 16px',
      paddingLG: '10px 20px',
    },
    // 输入框配置
    Input: {
      borderRadius: 4,
      borderColor: '#e5e5e5',
      focusBorderColor: '#333333',
    },
    // 卡片配置
    Card: {
      borderRadius: 6,
      boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
    },
    // 菜单配置
    Menu: {
      itemHoverBg: '#f5f5f5',
      itemActiveBg: '#f0f0f0',
      itemSelectedBg: '#f0f0f0',
      itemSelectedColor: '#333333',
    },
  },
};

export default minimalTheme;