(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('affixer', function($document, $window) {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                var top = attrs.affixerOffset;
                var topOffset = element[0].offsetTop - top;

                function affixElement() {
                    if ($window.pageYOffset > topOffset) {
                        element.css('position', 'fixed');
                        element.css('top', top + 'px');
                    } else {
                        element.css('position', '');
                        element.css('top', '');
                    }
                }

                angular.element($window).bind('scroll', affixElement);
            }
        };
    });
})();
