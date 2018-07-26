'use strict';

var gulp = require('gulp');
var plugins = require('gulp-load-plugins')();
var file = 'angular-auto-complete.js';

gulp.task('scripts', function () {
    return gulp.src(file)
        .pipe(plugins.eslint())
        .pipe(plugins.eslint.format());
    //.pipe(plugins.uglify())
    //.pipe(plugins.rename({ extname: '.min.js' }))
    //.pipe(gulp.dest(DEST));
});

gulp.task('default', ['scripts']);

gulp.task('watch', function () {
    gulp.watch(file, ['default']);
});
