(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('c3', function(DashboardSrv) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                chart: '=',
                resizeOn: '@',
                error: '=',
                height: '<?',
                hideActions: '<?',
                onSaveCsv: '&?'
            },
            templateUrl: 'views/directives/charts/c3.html',
            link: function(scope, element) {
                var binto = $(element).find('.c3-chart')[0];

                scope.initChart = function(chart) {
                    if (!_.isEmpty(chart)) {
                        scope.chart.bindto = binto;
                        scope.chart.color = {
                            pattern: DashboardSrv.colorsPattern
                        };
                        scope.chart.size = {
                            height: scope.height || 300
                        };
                        scope.c3 = c3.generate(scope.chart);
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
                    scope.$on(scope.resizeOn, function() {
                        if(scope.c3) {
                            scope.c3.resize();
                        }
                    });
                }
            }
        };
    });

})();
