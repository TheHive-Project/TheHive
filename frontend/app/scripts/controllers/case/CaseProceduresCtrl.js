(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseProceduresCtrl', CaseProceduresCtrl);

    function CaseProceduresCtrl($scope, $state, $stateParams, $uibModal, ModalUtilsSrv, AttackPatternSrv, FilteringSrv, CaseTabsSrv, ProcedureSrv, PaginatedQuerySrv, NotificationSrv) {
        var self = this;

        CaseTabsSrv.activateTab($state.current.data.tab);

        this.caseId = $stateParams.caseId;
        this.tactics = AttackPatternSrv.tactics.values;

        this.$onInit = function() {
            self.filtering = new FilteringSrv('procedure', 'procedure.list', {
                version: 'v1',
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-occurDate'],
                },
                defaultFilter: []
            });

            self.filtering.initContext(self.caseId)
                .then(function() {
                    self.load();

                    $scope.$watchCollection('$vm.list.pageSize', function (newValue) {
                        self.filtering.setPageSize(newValue);
                    });
                });
        };

        this.addProcedure = function() {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/case/procedures/add-procedure.modal.html',
                controller: 'AddProcedureModalCtrl',
                controllerAs: '$modal',
                size: 'lg',
                resolve: {
                    caseId: function() {
                        return self.caseId;
                    }
                }
            });

            return modalInstance.result
                .then(function() {
                    self.load();
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('ProcedureCtrl', err.data, err.status);
                    }
                });
        };

        this.updateDescription = function(procedure) {
            ProcedureSrv.update(procedure._id, {
                description: procedure.description
            }).then(function(response) {
                console.log(response);
            }).catch(function(err) {
                NotificationSrv.error('ProcedureCtrl', err.data, err.status);
            });
        };

        this.load = function() {
            self.list = new PaginatedQuerySrv({
                name: 'case-procedures',
                root: self.caseId,
                version: 'v1',
                scope: $scope,
                sort: self.filtering.context.sort,
                loadAll: false,
                pageSize: self.filtering.context.pageSize,
                filter: self.filtering.buildQuery(),
                operations: [
                    {'_name': 'getCase', "idOrName": self.caseId},
                    {'_name': 'procedures'}
                ],
                extraData: ['pattern']
            });
        };

        self.remove = function(procedure) {
            ModalUtilsSrv.confirm('Delete TTP', 'Are you sure you want to delete the selected tactic, technique and procedure?', {
                okText: 'Yes, remove it',
                flavor: 'danger'
            }).then(function() {
                ProcedureSrv.remove(procedure._id)
                    .then(function() {
                        self.load();
                        NotificationSrv.success('TTP has been successfully removed');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Procedure List', err.data, err.status);
                    });
            });
        };

        self.showPattern = function(patternId) {
            $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/attack/view.html',
                controller: 'AttackPatternDialogCtrl',
                controllerAs: '$modal',
                size: 'max',
                resolve: {
                    pattern: function() {
                        return AttackPatternSrv.get(patternId);
                    }
                }
            });
        };

        this.toggleFilters = function () {
            this.filtering.toggleFilters();
        };


        this.filter = function () {
            self.filtering.filter().then(this.applyFilters);
        };

        this.clearFilters = function () {
            this.filtering.clearFilters()
                .then(self.search);
        };

        this.addFilter = function (field, value) {
            self.filtering.addFilter(field, value).then(this.applyFilters);
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

        self.filterBy = function(field, value) {
            self.filtering.clearFilters()
                .then(function() {
                    self.addFilterValue(field, value);
                });
        };

        this.sortBy = function(sort) {
            self.list.sort = sort;
            self.list.update();
            self.filtering.setSort(sort);
        };

        self.sortByField = function(field) {
            var context = this.filtering.context;
            var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
            var sort = null;

            if(currentSort.substr(1) !== field) {
                sort = ['+' + field];
            } else {
                sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
            }

            self.list.sort = sort;
            self.list.update();
            self.filtering.setSort(sort);
        };
    }
}());
