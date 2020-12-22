(function() {
    'use strict';
    angular.module('theHiveControllers').controller('MainPageCtrl',
        function($rootScope, $scope, $window, $stateParams, $state, FilteringSrv, CaseTaskSrv, PaginatedQuerySrv, EntitySrv, UserSrv) {
            var self = this;
            var view = $stateParams.viewId;

            self.$onInit = function() {
                self.view = {};

                self.defaultFilter = {
                    _in: {
                        _field: 'status',
                        _values: ['Waiting', 'InProgress']
                    }
                };

                self.queryOperations = view === 'mytasks' ? [
                    {_name: 'currentUser'},
                    {_name: 'tasks'}
                ] : [
                    {_name: 'waitingTask'}
                ];

                if ($stateParams.viewId === 'mytasks') {
                    $rootScope.title = 'My tasks';
                    self.view.data = 'mytasks';

                } else if ($stateParams.viewId === 'waitingtasks') {
                    $rootScope.title = 'Waiting tasks';
                    self.view.data = 'waitingtasks';
                }

                self.filtering = new FilteringSrv('task', $stateParams.viewId + '.list', {
                    version: 'v1',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        sort: ['-flag', '-startDate'],
                    },
                    defaultFilter: [],
                    excludes: view === 'mytasks' ? ['owner'] : ['status']
                });
                self.filtering.initContext('list')
                    .then(function() {
                        self.load();

                        $scope.$watch('$vm.list.pageSize', function (newValue) {
                            self.filtering.setPageSize(newValue);
                        });
                    });
            };

            self.load = function() {
                self.list = new PaginatedQuerySrv({
                    objectType: 'case_task',
                    version: 'v1',
                    scope: $scope,
                    sort: self.filtering.context.sort,
                    loadAll: false,
                    pageSize: self.filtering.context.pageSize,
                    filter: self.filtering.buildQuery(),
                    baseFilter: view === 'mytasks' ? self.defaultFilter : [],
                    operations: self.queryOperations,
                    extraData: ['case', 'actionRequired'],
                    name: $stateParams.viewId
                });
            };

            self.toggleStats = function () {
                self.filtering.toggleStats();
            };

            self.toggleFilters = function () {
                self.filtering.toggleFilters();
            };

            self.filter = function () {
                self.filtering.filter().then(self.applyFilters);
            };

            self.clearFilters = function () {
                self.filtering.clearFilters()
                    .then(self.search);
            };

            self.removeFilter = function (index) {
                self.filtering.removeFilter(index)
                    .then(self.search);
            };

            self.search = function () {
                self.load();
                self.filtering.storeContext();
            };
            self.addFilterValue = function (field, value) {
                self.filtering.addFilterValue(field, value);
                self.search();
            };

            // init values
            self.showFlow = true;
            self.openEntity = EntitySrv.open;
            self.getUserInfo = UserSrv.getCache;

            self.openWTask = function(task) {
                if (task.status === 'Waiting') {
                    CaseTaskSrv.update({
                        'taskId': task._id
                    }, {
                        'status': 'InProgress'
                    }, function(data) {
                        if (data.status === 'InProgress') {
                            self.openEntity(task);
                        }
                    }, function(response) {
                        console.log(response);
                    });
                }
            };

            self.live = function() {
                $window.open($state.href('live'), 'TheHiveLive',
                    'width=500,height=700,menubar=no,status=no,toolbar=no,location=no,scrollbars=yes');
            };
        }
    );
})();
