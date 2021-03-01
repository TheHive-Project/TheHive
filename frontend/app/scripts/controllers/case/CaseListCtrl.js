(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseListCtrl', CaseListCtrl);

    function CaseListCtrl($scope, $q, $state, $window, $uibModal, StreamQuerySrv, FilteringSrv, SecuritySrv, StreamStatSrv, PaginatedQuerySrv, EntitySrv, CaseSrv, UserSrv, AuthenticationSrv, CaseResolutionStatus, NotificationSrv, Severity, Tlp, CortexSrv) {
        var self = this;

        this.openEntity = EntitySrv.open;
        this.getUserInfo = UserSrv.getCache;
        this.CaseResolutionStatus = CaseResolutionStatus;
        this.caseResponders = null;

        this.lastQuery = null;

        self.selection = [];
        self.menu = {
            selectAll: false
        };

        this.$onInit = function() {
            self.filtering = new FilteringSrv('case', 'case.list', {
                version: 'v1',
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-flag', '-startDate']
                },
                defaultFilter: [{
                    field: 'status',
                    type: 'enumeration',
                    value: {
                        list: [{
                            text: 'Open',
                            label: 'Open'
                        }]
                    }
                }]
            });

            self.filtering.initContext('list')
                .then(function() {
                    self.load();

                    $scope.$watch('$vm.list.pageSize', function (newValue) {
                        self.filtering.setPageSize(newValue);
                    });
                });


            // Case stats to build quick filter menu
            StreamQuerySrv('v1', [
                {
                    _name: 'listCase'
                },
                {
                    _name: 'aggregation',
                    _agg: 'field',
                    _field: 'status',
                    _select: [
                        {_agg: 'count'}
                    ]
                }
            ], {
                scope: $scope,
                rootId: 'any',
                objectType: 'case',
                query: {
                    params: {
                        name: 'case-status-stats'
                    }
                },
                onUpdate: function(updates) {
                    self.caseStats = updates;
                }
            });

            // Case total
            StreamQuerySrv('v1', [
                {_name: 'listCase'},
                {_name: 'count'}
            ], {
                scope: $scope,
                rootId: 'any',
                objectType: 'case',
                query: {
                    params: {
                        name: 'case-count-stats'
                    }
                },
                onUpdate: function(updates) {
                    self.caseCount = updates;
                }
            });

        };

        this.load = function() {

            this.list = new PaginatedQuerySrv({
                name: 'cases',
                root: undefined,
                objectType: 'case',
                version: 'v1',
                scope: $scope,
                sort: self.filtering.context.sort,
                loadAll: false,
                pageSize: self.filtering.context.pageSize,
                filter: this.filtering.buildQuery(),
                operations: [
                    {'_name': 'listCase'}
                ],
                extraData: ['observableStats', 'taskStats', 'isOwner', 'shareCount', 'permissions', 'actionRequired'],
                onUpdate: function() {
                    self.resetSelection();
                }
            });
        };

        self.resetSelection = function() {
            if (self.menu.selectAll) {
                self.selectAll();
            } else {
                self.selection = [];
                self.menu.selectAll = false;
                // self.updateMenu();
            }
        };

        self.select = function(caze) {
            if (caze.selected) {
                self.selection.push(caze);
            } else {
                self.selection = _.reject(self.selection, function(item) {
                    return item._id === caze._id;
                });
            }
            // self.updateMenu();
        };

        self.selectAll = function() {
            var selected = self.menu.selectAll;

            _.each(self.list.values, function(item) {
                if(SecuritySrv.checkPermissions(['manageCase'], item.extraData.permissions)) {
                    item.selected = selected;
                }
            });

            if (selected) {
                self.selection = _.filter(self.list.values, function(item) {
                    return !!item.selected;
                });
            } else {
                self.selection = [];
            }

            //self.updateMenu();

        };

        this.toggleStats = function () {
            this.filtering.toggleStats();
        };

        this.toggleFilters = function () {
            this.filtering.toggleFilters();
        };

        this.toggleAdvanced = function () {
            this.filtering.toggleAdvanced();
        };

        this.filter = function () {
            self.filtering.filter().then(this.applyFilters);
        };

        this.clearFilters = function () {
            this.filtering.clearFilters()
                .then(self.search);
        };

        this.removeFilter = function (index) {
            self.filtering.removeFilter(index)
                .then(self.search);
        };

        this.search = function () {
            self.load();
            self.filtering.storeContext();
        };
        this.addFilterValue = function (field, value) {
            this.filtering.addFilterValue(field, value);
            this.search();
        };

        this.filterMyCases = function() {
            this.filtering.clearFilters()
                .then(function() {
                    var currentUser = AuthenticationSrv.currentUser;
                    self.filtering.addFilter({
                        field: 'assignee',
                        type: 'user',
                        value: {
                            list: [{
                                text: currentUser.login,
                                label: currentUser.name
                            }]
                        }
                    });
                    self.search();
                });
        };

        this.filterMyOpenCases = function() {
            this.filtering.clearFilters()
                .then(function() {
                    var currentUser = AuthenticationSrv.currentUser;
                    self.filtering.addFilter({
                        field: 'assignee',
                        type: 'user',
                        value: {
                            list: [{
                                text: currentUser.login,
                                label: currentUser.name
                            }]
                        }
                    });
                    self.addFilterValue('status', 'Open');
                });
        };

        this.filterByStatus = function(status) {
            this.filtering.clearFilters()
                .then(function() {
                    self.addFilterValue('status', status);
                });
        };

        this.filterByResolutionStatus = function(status) {
            this.filtering.clearFilters()
                .then(function() {
                    self.filtering.addFilterValue('resolutionStatus', status);
                    self.addFilterValue('status', 'Resolved');
                });
        };

        this.sortBy = function(sort) {
            this.list.sort = sort;
            this.list.update();
            this.filtering.setSort(sort);
        };

        this.sortByField = function(field) {
            var context = this.filtering.context;
            var currentSort = Array.isArray(context.sort) ? _.without(context.sort, '-flag', '+flag')[0] : context.sort;
            var sort = null;

            if(currentSort.substr(1) !== field) {
                sort = ['-flag', '+' + field];
            } else {
                sort = ['-flag', (currentSort === '+' + field) ? '-'+field : '+'+field];
            }

            self.list.sort = sort;
            self.list.update();
            self.filtering.setSort(sort);
        };

        this.bulkEdit = function() {
            var modal = $uibModal.open({
                animation: 'true',
                templateUrl: 'views/partials/case/case.update.html',
                controller: 'CaseUpdateCtrl',
                controllerAs: '$dialog',
                size: 'lg',
                resolve: {
                    selection: function() {
                        return self.selection;
                    }
                }
            });

            modal.result.then(function(operations) {
                $q.all(_.map(operations, function(operation) {
                    return CaseSrv.bulkUpdate(operation.ids, operation.patch);
                })).then(function(/*responses*/) {
                    NotificationSrv.log('Selected cases have been updated successfully', 'success');
                });
            });
        };

        this.getCaseResponders = function(caze, force) {
            if (!force && this.caseResponders !== null) {
                return;
            }

            self.caseResponders = null;
            CortexSrv.getResponders('case', caze._id)
                .then(function(responders){
                    self.caseResponders = responders;
                    return CortexSrv.promntForResponder(responders);
                })
                .then(function(response) {
                    if(response && _.isString(response)) {
                        NotificationSrv.log(response, 'warning');
                    } else {
                        return CortexSrv.runResponder(response.id, response.name, 'case', _.pick(caze, '_id', 'tlp', 'pap'));
                    }
                })
                .then(function(response){
                    NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on case', caze.title].join(' '), 'success');
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('CaseList', err.data, err.status);
                    }
                });
        };
    }
})();
