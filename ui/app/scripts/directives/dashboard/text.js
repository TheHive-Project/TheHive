(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardText', function($q, $http, $state, DashboardSrv, GlobalSearchSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                options: '=',
                entity: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                resizeOn: '@',
                metadata: '='
            },
            templateUrl: 'views/directives/dashboard/text/view.html',
            link: function(scope, elem) {

                scope.error = false;
                scope.data = null;
                scope.globalQuery = null;

                scope.load = function() {
                    if(!scope.options.series || scope.options.series.length === 0) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);
                    scope.globalQuery = query;

                    var stats = {
                        stats: _.map(scope.options.series || [], function(serie, index) {
                            var s = {
                                _agg: serie.agg,
                                _name: serie.name || 'agg_' + (index + 1),
                                _query: serie.query || {}
                            };

                            if(serie.agg !== 'count') {
                                s._field = serie.field;
                            }

                            return {
                                model: serie.entity,
                                query: query,
                                stats: [s]
                            };
                        })
                    };

                    var statsPromise = $http.post('./api/_stats', stats);

                    statsPromise.then(function(response) {
                        scope.error = false;
                        scope.data = response.data;

                        var template = scope.options.template;
                        Object.keys(scope.data).forEach(function(key){
                            var regex = new RegExp('{{' + key + '}}', 'gi');

                            template = template.replace(regex, scope.data[key]);
                        });

                        scope.content = template;

                    }, function(err) {
                        scope.error = true;
                        NotificationSrv.error('dashboardBar', 'Failed to fetch data, please edit the widget definition', err.status);
                    });
                };

                scope.copyHTML = function() {
                    var html = elem[0].querySelector('.widget-content').innerHTML;
                    function listener(e) {
                        e.clipboardData.setData('text/html', html);
                        e.clipboardData.setData('text/plain', html);
                        e.preventDefault();
                    }
                    document.addEventListener('copy', listener);
                    document.execCommand('copy');
                    document.removeEventListener('copy', listener);
                }

                if (scope.autoload === true) {
                    scope.load();
                }

                if (!_.isEmpty(scope.refreshOn)) {
                    scope.$on(scope.refreshOn, function(event, filter) {
                        scope.filter = filter;
                        scope.load();
                    });
                }
            }
        };
    });
})();
