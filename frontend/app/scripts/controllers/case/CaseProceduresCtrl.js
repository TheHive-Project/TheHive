(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseProceduresCtrl', CaseProceduresCtrl);

    function CaseProceduresCtrl($scope, $state, $stateParams, $uibModal, ModalUtilsSrv, AttackPatternSrv, FilteringSrv, CaseTabsSrv, ProcedureSrv, PaginatedQuerySrv, NotificationSrv, AppLayoutSrv) {
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
                // keyboard: false,
                // backdrop: 'static',
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

        // $scope.state = {
        //     isNewTask: false,
        //     showGrouped: !!AppLayoutSrv.layout.groupTasks
        // };
        // $scope.newTask = {
        //     status: 'Waiting'
        // };
        // $scope.taskResponders = null;
        // $scope.collapseOptions = {};

        // $scope.getAssignableUsers = function(taskId) {
        //     return [
        //         {_name: 'getTask', idOrName: taskId},
        //         {_name: 'assignableUsers'}
        //     ];
        // };

        this.load = function() {
            self.list = new PaginatedQuerySrv({
                name: 'case-procedures',
                root: self.caseId,
                // objectType: 'case_task',
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

        // $scope.filterMyTasks = function() {
        //     $scope.filtering.clearFilters()
        //         .then(function() {
        //             var currentUser = AuthenticationSrv.currentUser;
        //             $scope.filtering.addFilter({
        //                 field: 'assignee',
        //                 type: 'user',
        //                 value: {
        //                     list: [{
        //                         text: currentUser.login,
        //                         label: currentUser.name
        //                     }]
        //                 }
        //             });
        //             $scope.search();
        //         });
        // };

        // $scope.toggleGroupedView = function() {
        //     $scope.state.showGrouped = !$scope.state.showGrouped;
        //
        //     AppLayoutSrv.groupTasks($scope.state.showGrouped);
        // };

        // $scope.buildTaskGroups = function(tasks) {
        //     // Sort tasks by order
        //     var orderedTasks = _.sortBy(_.map(tasks, function(t) {
        //         return _.pick(t, 'group', 'order');
        //     }), 'order');
        //     var groups = [];
        //
        //     // Get group names by keeping the group orders
        //     _.each(orderedTasks, function(task) {
        //         if(groups.indexOf(task.group) === -1) {
        //             groups.push(task.group);
        //         }
        //     });
        //
        //     var groupedTasks = [];
        //     _.each(groups, function(group) {
        //         groupedTasks.push({
        //             group: group,
        //             tasks: _.filter(tasks, function(t) {
        //                 return t.group === group;
        //             })
        //         });
        //     });
        //
        //     $scope.groups = groups;
        //     $scope.groupedTasks = groupedTasks;
        // };

        // $scope.showTask = function(taskId) {
        //     $state.go('app.case.tasks-item', {
        //         itemId: taskId
        //     });
        // };

        // $scope.updateField = function (fieldName, newValue, task) {
        //     var field = {};
        //     field[fieldName] = newValue;
        //     return CaseTaskSrv.update({
        //         taskId: task._id
        //     }, field, function () {}, function (response) {
        //         NotificationSrv.error('taskList', response.data, response.status);
        //     });
        // };

        // $scope.addTask = function() {
        //     CaseTaskSrv.save({
        //         'caseId': $scope.caseId,
        //         'flag': false
        //     }, $scope.newTask, function() {
        //         $scope.isNewTask = false;
        //         $scope.newTask.title = '';
        //         $scope.newTask.group = '';
        //         NotificationSrv.success('Task has been successfully added');
        //     }, function(response) {
        //         NotificationSrv.error('taskList', response.data, response.status);
        //     });
        // };
        //
        // $scope.removeTask = function(task) {
        //
        //     ModalUtilsSrv.confirm('Delete task', 'Are you sure you want to delete the selected task?', {
        //         okText: 'Yes, remove it',
        //         flavor: 'danger'
        //     }).then(function() {
        //         CaseTaskSrv.update({
        //             'taskId': task._id
        //         }, {
        //             status: 'Cancel'
        //         }, function() {
        //             $scope.$emit('tasks:task-removed', task);
        //             NotificationSrv.success('Task has been successfully removed');
        //         }, function(response) {
        //             NotificationSrv.error('taskList', response.data, response.status);
        //         });
        //     });
        // };
    }
}());
