(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('c3', function() {
        return {
            restrict: 'E',
            scope: {
                chart: '=',
                resizeOn: '@'
            },
            templateUrl: 'views/directives/charts/c3.html',
            link: function(scope, element) {
                var binto = $(element).find('.c3-chart')[0];

                scope.initChart = function(chart) {
                    if (!_.isEmpty(chart)) {
                        scope.chart.bindto = binto;
                        scope.chart.size = {
                            height: 300
                        };
                        scope.c3 = c3.generate(scope.chart);
                        // scope.c3.resize();
                    }
                };

                scope.save = function() {
                    saveSvgAsPng(($(element).find('.c3-chart > svg')[0]), "chart.png", {
                        backgroundColor: '#FFF'
                    });
                };

                scope.$watch('chart', function(newValue) {
                    scope.initChart(newValue);
                });

                if(scope.resizeOn) {
                    //console.log(scope.resizeOn);
                    scope.$on(scope.resizeOn, function() {
                        if(scope.c3) {
                            scope.c3.resize();
                        }
                    })
                }
            }
        };
    });

})();
