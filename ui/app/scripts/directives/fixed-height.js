(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('fixedHeight', function($window, $timeout) {
        return {
            restrict: 'A',
            link: function(scope, elem) {

                $timeout(function() {
                    var windowHeight = $(window).height();
                    var footerHeight = $('.main-footer').outerHeight();
                    var headerHeight = $('.main-header').height();

                    elem.css('min-height', (windowHeight - headerHeight - footerHeight) + "px");
                }, 500);

                angular.element($window).bind('resize', function() {
                    var windowHeight = $(window).height();
                    var footerHeight = $('.main-footer').outerHeight();
                    var headerHeight = $('.main-header').height();

                    elem.css('min-height', (windowHeight - headerHeight - footerHeight) + "px");
                });

            }
        };
    });

})();
