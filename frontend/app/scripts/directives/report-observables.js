(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('reportObservables', function($q, $filter, $uibModal) {
        return {
            restrict: 'E',
            scope: {
                origin: '=',
                observables: '=',
                analyzer: '=',
                caseId: '=',
                permissions: '=',
                onRefresh: '&?'
            },
            templateUrl: 'views/directives/report-observables.html',
            link: function(scope) {
                scope.$watch('observables', function() {
                    scope.selected = 0;
                    scope.groups = _.groupBy(scope.observables, 'dataType');
                    scope.pagination = {
                        pageSize: 10,
                        currentPage: 1,
                        filter: '',
                        data: scope.observables
                    };

                    _.each(scope.observables, function(item) {
                        item.imported = !!item.stats.observableId;
                    });
                });
            },
            controller: function($scope) {
                $scope.filterArtifacts = function(type) {
                    $scope.pagination.filter = type;
                    $scope.pagination.currentPage = 1;

                    if(type !== '') {
                        $scope.pagination.data = $scope.groups[type];
                    } else {
                        $scope.pagination.data = $scope.observables;
                    }
                };

                $scope.selectObservable = function(observable) {
                    if(!!observable.stats.imported) {
                        return;
                    }
                    if(observable.selected === true) {
                        $scope.selected++;
                    } else {
                        $scope.selected--;
                    }
                };

                $scope.selectAll = function() {
                    var type = $scope.pagination.filter;
                    _.each(type === '' ? $scope.observables : $scope.groups[type], function(item) {
                        //if(!item.id && !item.selected) {
                        if(!item.imported && !item.selected) {
                            item.selected = true;
                            $scope.selected++;
                        }
                    });
                };

                $scope.clearSelection = function() {
                    _.each($scope.observables, function(item) {
                        delete item.selected;
                    });
                    $scope.selected = 0;
                };

                $scope.import = function() {
                    var toImport = _.groupBy(_.filter($scope.observables, function(item) {
                        return item.selected === true;
                    }), 'dataType');

                    var message = [
                        '### Discovered from:',
                        '- Observable: **['+ $scope.origin.dataType + '] - ' + $filter('fang')($scope.origin.data) + '**',
                        '- Analyzer: **'+ $scope.analyzer + '**'
                    ].join('\n');

                    _.each(toImport, function(list, key) {
                        var params = {
                            dataType: key,
                            single: list.length === 1,
                            ioc: false,
                            sighted: false,
                            ignoreSimilarity: false,
                            tlp: 2,
                            message: message,
                            tags: [{text: 'src:' + $scope.analyzer}]
                        };

                        if(key === 'file') {
                            params.attachment = _.pluck(list, 'attachment');
                            params.isUpload = false;
                        } else {
                            params.data = _.pluck(list, 'data').join('\n');
                        }

                        var modal = $uibModal.open({
                            animation: 'true',
                            templateUrl: 'views/partials/observables/observable.creation.html',
                            controller: 'ObservableCreationCtrl',
                            size: 'lg',
                            resolve: {
                                params: function() {
                                    return params;
                                }
                            }
                        });

                        modal.result
                          .then(function(/*response*/) {
                              _.each(list, function(item) {
                                  //item.id = true;
                                  item.imported = true;
                                  item.selected = false;
                              });

                              if($scope.onRefresh) {
                                  $scope.onRefresh();
                              }
                          });
                    });


                };
            }
        };
    });

})();
