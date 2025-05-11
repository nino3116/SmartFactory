/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,js,jsx,ts,tsx,vue,hbs,mustache,thymeleaf}", // Frontend 개발 파일
    "../SpringProject/src/main/resources/templates/**/*.{html,thymeleaf}", // Spring Boot 템플릿 파일 경로 (확장자에 따라 수정)
    "./src/**/*.css", // src에 costom할 css파일있으면 가져다 쓰기위해
    "./node_modules/flowbite/**/*.js" // flowbite js파일을 쓰기위해 설정
  ],
  theme: {
    extend: {},
  },
  plugins: [
    require('flowbite/plugin')
  ],
};
