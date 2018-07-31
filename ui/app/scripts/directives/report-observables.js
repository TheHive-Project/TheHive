(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('reportObservables', function($q, $filter, $uibModal, UtilsSrv) {
        return {
            restrict: 'E',
            scope: {
                origin: '=',
                observables: '=',
                analyzer: '=',
                caseId: '='
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
                })
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
                    if(observable.id) {
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
                        if(!item.id && !item.selected) {
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

                    var keys = _.keys(toImport);
                    var promises = [];
                    var message = [
                        '### Discovered from:',
                        '- Observable: **['+ $scope.origin.dataType + '] - ' + $filter('fang')($scope.origin.data) + '**',
                        '- Analyzer: **'+ $scope.analyzer + '**'
                    ].join('\n')

                    _.each(toImport, function(list, key) {

                        var modal = $uibModal.open({
                            animation: 'true',
                            templateUrl: 'views/partials/observables/observable.creation.html',
                            controller: 'ObservableCreationCtrl',
                            size: 'lg',
                            resolve: {
                                params: function() {
                                    return {
                                        dataType: key,
                                        bulk: true,
                                        ioc: false,
                                        sighted: false,
                                        data: _.pluck(list, 'data').join('\n'),
                                        tlp: 2,
                                        message: message,
                                        tags: [],
                                        tagNames: ''
                                    }
                                },
                                tags: function() {
                                    return [{text: 'src:' + $scope.analyzer}]
                                }
                            }
                        });

                        modal.result
                          .then(function(response) {
                              _.each(list, function(item) {
                                  item.id = true;
                                  item.selected = false;
                              });
                          });
                    });


                };
            }
        };
    });

})();
