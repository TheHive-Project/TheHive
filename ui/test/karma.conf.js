// Karma configuration
// http://karma-runner.github.io/0.12/config/configuration-file.html
// Generated on 2015-11-27 using
// generator-karma 1.0.0

module.exports = function(config) {
  'use strict';

  config.set({
    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '../',

    // testing framework to use (jasmine/mocha/qunit/...)
    // as well as any additional frameworks (requirejs/chai/sinon/...)
    frameworks: [
      "jasmine"
    ],

    // list of files / patterns to load in the browser
    files: [
      // bower:js
      'bower_components/es5-shim/es5-shim.js',
      'bower_components/jquery/dist/jquery.js',
      'bower_components/angular/angular.js',
      'bower_components/angular-animate/angular-animate.js',
      'bower_components/angular-bootstrap/ui-bootstrap-tpls.js',
      'bower_components/angular-cookies/angular-cookies.js',
      'bower_components/lodash/dist/lodash.compat.js',
      'bower_components/angular-json-human/dist/angular-json-human.js',
      'bower_components/angular-sanitize/angular-sanitize.js',
      'bower_components/showdown/src/showdown.js',
      'bower_components/angular-markdown-directive/markdown.js',
      'bower_components/moment/moment.js',
      'bower_components/angular-moment/angular-moment.js',
      'bower_components/angular-resource/angular-resource.js',
      'bower_components/humanize-duration/humanize-duration.js',
      'bower_components/angular-timer/dist/angular-timer.js',
      'bower_components/angular-touch/angular-touch.js',
      'bower_components/angular-ui-router/release/angular-ui-router.js',
      'bower_components/bootstrap/dist/js/bootstrap.js',
      'bower_components/bootstrap-sass-official/assets/javascripts/bootstrap.js',
      'bower_components/dropzone/dist/min/dropzone.min.js',
      'bower_components/ng-csv/build/ng-csv.min.js',
      'bower_components/ng-tags-input/ng-tags-input.js',
      'bower_components/underscore/underscore.js',
      'bower_components/angular-ui-notification/dist/angular-ui-notification.js',
      'bower_components/d3/d3.js',
      'bower_components/c3/c3.js',
      'bower_components/angular-messages/angular-messages.js',
      'bower_components/ng-file-upload/ng-file-upload.js',
      'bower_components/ng-file-upload-shim/ng-file-upload-shim.js',
      'bower_components/es6-shim/es6-shim.js',
      'bower_components/angular-clipboard/angular-clipboard.js',
      'bower_components/angular-local-storage/dist/angular-local-storage.js',
      'bower_components/myforce-angularjs-dropdown-multiselect/src/angularjs-dropdown-multiselect.js',
      'bower_components/angular-highlightjs/build/angular-highlightjs.js',
      'bower_components/marked/lib/marked.js',
      'bower_components/angular-marked/dist/angular-marked.js',
      'bower_components/bootstrap-markdown/js/bootstrap-markdown.js',
      'bower_components/angular-markdown-editor-ghiscoding/src/angular-markdown-editor.js',
      'bower_components/angular-ui-ace/ui-ace.js',
      'bower_components/angular-page-loader/dist/angular-page-loader.js',
      'bower_components/angular-images-resizer/angular-images-resizer.js',
      'bower_components/angular-base64-upload/src/angular-base64-upload.js',
      'bower_components/jquery-ui/jquery-ui.js',
      'bower_components/angular-ui-sortable/sortable.js',
      'bower_components/js-base64/base64.js',
      'bower_components/angular-mocks/angular-mocks.js',
      // endbower
      "bower_components/cryptojslib/components/core-min.js",
      "bower_components/cryptojslib/components/sha256-min.js",
      "app/scripts/**/*.js",
      "test/mock/**/*.js",
      "test/spec/**/*.js"
    ],

    // list of files / patterns to exclude
    exclude: [
    ],

    // web server port
    port: 8080,

    // Start these browsers, currently available:
    // - Chrome
    // - ChromeCanary
    // - Firefox
    // - Opera
    // - Safari (only Mac)
    // - PhantomJS
    // - IE (only Windows)
    browsers: [
      "PhantomJS"
    ],

    // Which plugins to enable
    plugins: [
      "karma-phantomjs-launcher",
      "karma-jasmine"
    ],

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: false,

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.LOG_INFO,

    // Uncomment the following lines if you are using grunt's server to run the tests
    // proxies: {
    //   '/': 'http://localhost:9000/'
    // },
    // URL root prevent conflicts with the site root
    // urlRoot: '_karma_'
  });
};
