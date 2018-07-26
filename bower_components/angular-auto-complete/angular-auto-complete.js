(function (global, factory) {
    'use strict';

    if (typeof exports === 'object' && typeof module !== 'undefined') {
        // commonJS
        module.exports = factory(require('angular'));
    }
    else if (typeof define === 'function' && define.amd) {
        // AMD
        define(['module', 'angular'], function (module, angular) {
            module.exports = factory(angular);
        });
    }
    else {
        factory(global.angular);
    }
}(this, function (angular) {
    var helperService = new HelperService();

    angular
        .module('autoCompleteModule', ['ngSanitize'])
        .directive('autoComplete', autoCompleteDirective)
        .directive('autoCompleteItem', autoCompleteItemDirective)
        .directive('autoCompleteNoMatch', autoCompleteNoMatchDirective);

    autoCompleteDirective.$inject = ['$q', '$compile', '$document', '$window', '$timeout'];
    function autoCompleteDirective($q, $compile, $document, $window, $timeout) {

        return {
            restrict: 'A',
            scope: {},
            transclude: false,
            controllerAs: 'ctrl',
            bindToController: {
                initialOptions: '&autoComplete'
            },
            require: ['autoComplete', 'ngModel'],
            link: postLinkFn,
            controller: MainCtrl
        };

        function postLinkFn(scope, element, attrs, ctrls) {
            var ctrl = ctrls[0]; //directive controller
            ctrl.textModelCtrl = ctrls[1]; // textbox model controller

            // store the jquery element on the controller
            ctrl.target = element;

            $timeout(function () {
                // execute the options expression
                $q.when(ctrl.initialOptions()).then(_initialize);
            });

            function _initialize(options) {
                options = options || {};

                ctrl.init(angular.extend({}, defaultOptions, options));

                _initializeContainer();
                _wireupEvents();
            }

            function _initializeContainer() {
                ctrl.container = _getContainer();

                if (ctrl.options.containerCssClass) {
                    ctrl.container.addClass(ctrl.options.containerCssClass);
                }

                // if a jquery parent is specified in options append the container to that
                // otherwise append to body
                if (ctrl.options.dropdownParent) {
                    ctrl.options.dropdownParent.append(ctrl.container);
                }
                else {
                    $document.find('body').append(ctrl.container);
                    ctrl.container.addClass('auto-complete-absolute-container');
                }

                // keep a reference to the <ul> element
                ctrl.elementUL = angular.element(ctrl.container[0].querySelector('ul.auto-complete-results'));
            }

            function _getContainer() {
                if (angular.isElement(ctrl.options.dropdownParent)) {
                    return _getCustomContainer();
                }

                return _getDefaultContainer();
            }

            function _getCustomContainer() {
                var container = ctrl.options.dropdownParent;

                container.addClass('auto-complete-container unselectable');
                container.attr('data-instance-id', ctrl.instanceId);

                var linkFn = $compile(_getDropdownListTemplate());
                var elementUL = linkFn(scope);
                container.append(elementUL);

                return container;
            }

            function _getDefaultContainer() {
                var linkFn = $compile(_getContainerTemplate());
                return linkFn(scope);
            }

            function _getContainerTemplate() {
                var html = '';
                html += '<div class="auto-complete-container unselectable"';
                html += '     data-instance-id="{{ ctrl.instanceId }}"';
                html += '     ng-show="ctrl.containerVisible">';
                html += _getDropdownListTemplate();
                html += '</div>';

                return html;
            }

            function _getDropdownListTemplate() {
                var html = '';
                html += '     <ul class="auto-complete-results">';
                html += '         <li ng-if="ctrl.renderItems.length"';
                html += '             ng-repeat="renderItem in ctrl.renderItems track by renderItem.id"';
                html += '             ng-click="ctrl.selectItem($index, true)"';
                html += '             class="auto-complete-item" data-index="{{ $index }}"';
                html += '             ng-class="ctrl.getSelectedCssClass($index)">';
                html += '               <auto-complete-item index="$index"';
                html += '                      item-template-link-fn="ctrl.itemTemplateLinkFn"';
                html += '                      render-item="renderItem"';
                html += '                      search-text="ctrl.searchText" />';
                html += '         </li>';
                html += '         <li ng-if="!ctrl.renderItems.length && ctrl.options.noMatchTemplateEnabled"';
                html += '             class="auto-complete-item auto-complete-no-match">';
                html += '               <auto-complete-no-match';
                html += '                      template="ctrl.options.noMatchTemplate"';
                html += '                      search-text="ctrl.searchText" />';
                html += '         </li>';
                html += '     </ul>';

                return html;
            }

            function _wireupEvents() {

                // when the target(textbox) gets focus activate the corresponding container
                element.on(DOM_EVENT.FOCUS, function () {
                    scope.$evalAsync(function () {
                        ctrl.activate();
                        if (ctrl.options.activateOnFocus) {
                            _waitAndQuery(element.val(), 100);
                        }
                    });
                });

                element.on(DOM_EVENT.INPUT, function () {
                    scope.$evalAsync(function () {
                        _tryQuery(element.val());
                    });
                });

                element.on(DOM_EVENT.KEYDOWN, function (event) {
                    var $event = event;
                    scope.$evalAsync(function () {
                        _handleElementKeyDown($event);
                    });
                });

                ctrl.container.find('ul').on(DOM_EVENT.SCROLL, function () {
                    if (!ctrl.options.pagingEnabled) {
                        return;
                    }

                    var list = this;
                    scope.$evalAsync(function () {
                        if (!ctrl.containerVisible) {
                            return;
                        }

                        // scrolled to the bottom?
                        if ((list.offsetHeight + list.scrollTop) >= list.scrollHeight) {
                            ctrl.tryLoadNextPage();
                        }
                    });
                });

                $document.on(DOM_EVENT.KEYDOWN, function (event) {
                    var $event = event;
                    scope.$evalAsync(function () {
                        _handleDocumentKeyDown($event);
                    });
                });

                $document.on(DOM_EVENT.CLICK, function (event) {
                    var $event = event;
                    scope.$evalAsync(function () {
                        _handleDocumentClick($event);
                    });
                });

                // $window is a reference to the browser's window object
                angular.element($window).on(DOM_EVENT.RESIZE, function () {
                    if (ctrl.options.hideDropdownOnWindowResize) {
                        scope.$evalAsync(function () {
                            ctrl.autoHide();
                        });
                    }
                });
            }

            function _ignoreKeyCode(keyCode) {
                return [
                    KEYCODE.TAB,
                    KEYCODE.ALT,
                    KEYCODE.CTRL,
                    KEYCODE.LEFTARROW,
                    KEYCODE.RIGHTARROW,
                    KEYCODE.MAC_COMMAND_LEFT,
                    KEYCODE.MAC_COMMAND_RIGHT
                ].indexOf(keyCode) !== -1;
            }

            function _handleElementKeyDown(event) {
                var keyCode = event.charCode || event.keyCode || 0;

                if (_ignoreKeyCode(keyCode)) {
                    return;
                }

                switch (keyCode) {
                    case KEYCODE.UPARROW:
                        ctrl.scrollToPreviousItem();

                        event.stopPropagation();
                        event.preventDefault();

                        break;

                    case KEYCODE.DOWNARROW:
                        ctrl.scrollToNextItem();

                        event.stopPropagation();
                        event.preventDefault();

                        break;

                    case KEYCODE.ENTER:
                        ctrl.selectItem(ctrl.selectedIndex, true);

                        //prevent postback upon hitting enter
                        event.preventDefault();
                        event.stopPropagation();

                        break;

                    case KEYCODE.ESCAPE:
                        ctrl.restoreOriginalText();
                        ctrl.autoHide();

                        event.preventDefault();
                        event.stopPropagation();

                        break;

                    default:
                        break;
                }
            }

            function _handleDocumentKeyDown() {
                // hide inactive dropdowns when multiple auto complete exist on a page
                helperService.hideAllInactive();
            }

            function _handleDocumentClick(event) {
                // hide inactive dropdowns when multiple auto complete exist on a page
                helperService.hideAllInactive();

                // ignore inline
                if (ctrl.isInline()) {
                    return;
                }

                // no container. probably destroyed in scope $destroy
                if (!ctrl.container) {
                    return;
                }

                // ignore target click
                if (event.target === ctrl.target[0]) {
                    event.stopPropagation();
                    return;
                }

                if (_containerContainsTarget(event.target)) {
                    event.stopPropagation();
                    return;
                }

                ctrl.autoHide();
            }

            function _tryQuery(searchText) {
                // query only if minimum number of chars are typed; else hide dropdown
                if ((ctrl.options.minimumChars === 0)
                    || (searchText && searchText.trim().length !== 0 && searchText.length >= ctrl.options.minimumChars)) {
                    _waitAndQuery(searchText);
                    return;
                }

                ctrl.autoHide();
            }

            function _waitAndQuery(searchText, delay) {
                // wait few millisecs before calling query(); this to check if the user has stopped typing
                var promise = $timeout(function () {
                    // has searchText unchanged?
                    if (searchText === element.val()) {
                        ctrl.query(searchText);
                    }

                    //cancel the timeout
                    $timeout.cancel(promise);

                }, (delay || 300));
            }

            function _containerContainsTarget(target) {
                // use native Node.contains
                // https://developer.mozilla.org/en-US/docs/Web/API/Node/contains
                var container = ctrl.container[0];
                if (angular.isFunction(container.contains) && container.contains(target)) {
                    return true;
                }

                // otherwise use .has() if jQuery is available
                if (window.jQuery && angular.isFunction(ctrl.container.has) &&
                    ctrl.container.has(target).length > 0) {

                    return true;
                }

                // assume target is not in container
                return false;
            }

            // cleanup on destroy
            var destroyFn = scope.$on('$destroy', function () {
                if (ctrl.container) {
                    ctrl.container.remove();
                    ctrl.container = null;
                }

                destroyFn();
            });
        }
    }

    MainCtrl.$inject = ['$q', '$window', '$document', '$timeout', '$templateRequest', '$compile', '$exceptionHandler'];
    function MainCtrl($q, $window, $document, $timeout, $templateRequest, $compile, $exceptionHandler) {
        var that = this;
        var originalSearchText = null;
        var queryCounter = 0;
        var dataLoadInProgress = false;
        var endOfPagedList = false;
        var currentPageIndex = 0;

        this.target = null;
        this.instanceId = -1;
        this.selectedIndex = -1;
        this.renderItems = [];
        this.containerVisible = false;
        this.searchText = null;
        this.itemTemplateLinkFn = null;

        this.isInline = function () {
            // if a dropdown jquery parent is provided it is assumed inline
            return angular.isElement(that.options.dropdownParent);
        };

        this.init = function (options) {
            that.instanceId = helperService.registerInstance(that);
            that.options = options;
            that.containerVisible = that.isInline();

            _safeCallback(that.options.ready, publicApi);
        };

        this.activate = function () {
            helperService.setActiveInstanceId(that.instanceId);
            // do not reset if the container (dropdown list) is currently visible
            // Ex: Switching to a different tab or window and switching back
            // again when the dropdown list is visible.
            if (!that.containerVisible) {
                originalSearchText = that.searchText = null;
            }
        };

        this.query = function (searchText) {
            that.empty();
            _reset();

            return _query(searchText, 0);
        };

        this.show = function () {
            // the show() method is called after the items are ready for display
            // the textbox position can change (ex: window resize) when it has focus
            // so reposition the dropdown before it's shown
            _positionDropdown();

            // callback
            _safeCallback(that.options.dropdownShown);
        };

        this.autoHide = function () {
            if (that.options && that.options.autoHideDropdown) {
                _hideDropdown();
            }
        };

        this.empty = function () {
            that.selectedIndex = -1;
            that.renderItems = [];
        };

        this.restoreOriginalText = function () {
            if (!originalSearchText) {
                return;
            }

            _setTargetValue(originalSearchText);
        };

        this.scrollToPreviousItem = function () {
            var itemIndex = _getItemIndexFromOffset(-1);
            if (itemIndex === -1) {
                return;
            }

            _scrollToItem(itemIndex);
        };

        this.scrollToNextItem = function () {
            var itemIndex = _getItemIndexFromOffset(1);
            if (itemIndex === -1) {
                return;
            }

            _scrollToItem(itemIndex);

            if (_shouldLoadNextPageAtItemIndex(itemIndex)) {
                _loadNextPage();
            }
        };

        this.selectItem = function (itemIndex, closeDropdownAndRaiseCallback) {
            var item = that.renderItems[itemIndex];
            if (!item) {
                return;
            }

            that.selectedIndex = itemIndex;

            _updateTarget();

            if (closeDropdownAndRaiseCallback) {
                that.autoHide();

                _safeCallback(that.options.itemSelected, { item: item.data });
            }
        };

        this.getSelectedCssClass = function (itemIndex) {
            return (itemIndex === that.selectedIndex) ? that.options.selectedCssClass : '';
        };

        this.tryLoadNextPage = function () {
            if (_shouldLoadNextPage()) {
                _loadNextPage();
            }
        };


        function _loadNextPage() {
            return _query(originalSearchText, (currentPageIndex + 1));
        }

        function _query(searchText, pageIndex) {
            var params = {
                searchText: searchText,
                paging: {
                    pageIndex: pageIndex,
                    pageSize: that.options.pageSize
                },
                queryId: ++queryCounter
            };

            var renderListFn = (that.options.pagingEnabled ? _renderPagedList : _renderList);

            return _queryInternal(params, renderListFn.bind(that, params));
        }

        function _queryInternal(params, renderListFn) {
            // backup original search term in case we need to restore if user hits ESCAPE
            originalSearchText = params.searchText;

            dataLoadInProgress = true;

            _safeCallback(that.options.loading);

            return $q.when(that.options.data(params.searchText, params.paging),
                function successCallback(result) {
                    // verify that the queryId did not change since the possibility exists that the
                    // search text changed before the 'data' promise was resolved. Say, due to a lag
                    // in getting data from a remote web service.
                    if (_didQueryIdChange(params)) {
                        that.autoHide();
                        return;
                    }

                    if (_shouldHideDropdown(params, result)) {
                        that.autoHide();
                        return;
                    }

                    renderListFn(result).then(function () {
                        that.searchText = params.searchText;
                        that.show();
                    });

                    // callback
                    _safeCallback(that.options.loadingComplete);
                },
                function errorCallback(error) {
                    that.autoHide();

                    _safeCallback(that.options.loadingComplete, { error: error });
                }).then(function () {
                    dataLoadInProgress = false;
                });
        }

        function _getItemIndexFromOffset(itemOffset) {
            var itemIndex = that.selectedIndex + itemOffset;

            if (itemIndex >= that.renderItems.length) {
                return -1;
            }

            return itemIndex;
        }

        function _scrollToItem(itemIndex) {
            if (!that.containerVisible) {
                return;
            }

            that.selectItem(itemIndex);

            var attrSelector = 'li[data-index="' + itemIndex + '"]';

            // use jquery.scrollTo plugin if available
            // http://flesler.blogspot.com/2007/10/jqueryscrollto.html
            if (window.jQuery && window.jQuery.scrollTo) {  // requires jquery to be loaded
                that.elementUL.scrollTo(that.elementUL.find(attrSelector));
                return;
            }

            var li = that.elementUL[0].querySelector(attrSelector);
            if (li) {
                // this was causing the page to jump/scroll
                //    li.scrollIntoView(true);
                that.elementUL[0].scrollTop = li.offsetTop;
            }
        }

        function _safeCallback(fn, args) {
            if (!angular.isFunction(fn)) {
                return;
            }

            try {
                return fn.call(that.target, args);
            } catch (ex) {
                //ignore
            }
        }

        function _positionDropdownIfVisible() {
            if (that.containerVisible) {
                _positionDropdown();
            }
        }

        function _positionDropdown() {
            // no need to position if container has been appended to
            // parent specified in options
            if (that.isInline()) {
                return;
            }

            var dropdownWidth = null;
            if (that.options.dropdownWidth && that.options.dropdownWidth !== 'auto') {
                dropdownWidth = that.options.dropdownWidth;
            }
            else {
                // same as textbox width
                dropdownWidth = that.target[0].getBoundingClientRect().width + 'px';
            }
            that.container.css({ 'width': dropdownWidth });

            if (that.options.dropdownHeight && that.options.dropdownHeight !== 'auto') {
                that.elementUL.css({ 'max-height': that.options.dropdownHeight });
            }

            // use the .position() function from jquery.ui if available (requires both jquery and jquery-ui)
            var hasJQueryUI = !!(window.jQuery && window.jQuery.ui);
            if (that.options.positionUsingJQuery && hasJQueryUI) {
                _positionUsingJQuery();
            }
            else {
                _positionUsingDomAPI();
            }
        }

        function _positionUsingJQuery() {
            var defaultPosition = {
                my: 'left top',
                at: 'left bottom',
                of: that.target,
                collision: 'none flip'
            };

            var position = angular.extend({}, defaultPosition, that.options.positionUsing);

            // jquery.ui position() requires the container to be visible to calculate its position.
            if (!that.containerVisible) {
                that.container.css({ 'visibility': 'hidden' });
            }
            that.containerVisible = true; // used in the template to set ng-show.
            $timeout(function () {
                that.container.position(position);
                that.container.css({ 'visibility': 'visible' });
            });
        }

        function _positionUsingDomAPI() {
            var rect = that.target[0].getBoundingClientRect();
            var DOCUMENT = $document[0];

            var scrollTop = DOCUMENT.body.scrollTop || DOCUMENT.documentElement.scrollTop || $window.pageYOffset;
            var scrollLeft = DOCUMENT.body.scrollLeft || DOCUMENT.documentElement.scrollLeft || $window.pageXOffset;

            that.container.css({
                'left': rect.left + scrollLeft + 'px',
                'top': rect.top + rect.height + scrollTop + 'px'
            });

            that.containerVisible = true;
        }

        function _updateTarget() {
            var item = that.renderItems[that.selectedIndex];
            if (!item) {
                return;
            }

            _setTargetValue(item.value);
        }

        function _setTargetValue(value) {
            that.target.val(value);
            that.textModelCtrl.$setViewValue(value);
        }

        function _hideDropdown() {
            if (that.isInline() || !that.containerVisible) {
                return;
            }

            // reset scroll position
            //that.elementUL[0].scrollTop = 0;
            that.containerVisible = false;
            that.empty();

            _reset();

            // callback
            _safeCallback(that.options.dropdownHidden);
        }

        function _shouldHideDropdown(params, result) {
            // do not hide the dropdown if the no match template is enabled
            // because the no match template is rendered within the dropdown container
            if (that.options.noMatchTemplateEnabled) {
                return false;
            }

            // do we have results to render?
            if (!_.isEmpty(result)) {
                return false;
            }

            // if paging is enabled hide the dropdown only when rendering the first page
            if (that.options.pagingEnabled) {
                return (params.paging.pageIndex === 0);
            }

            return true;
        }

        function _didQueryIdChange(params) {
            return (params.queryId !== queryCounter);
        }

        function _renderList(params, result) {
            return _getRenderFn().then(function (renderFn) {
                if (_.isEmpty(result)) {
                    return;
                }

                that.renderItems = _renderItems(renderFn, result);
            });
        }

        function _renderPagedList(params, result) {
            return _getRenderFn().then(function (renderFn) {
                if (_.isEmpty(result)) {
                    return;
                }

                var items = _renderItems(renderFn, result);

                // in case of paged list we add to the array instead of replacing it
                angular.forEach(items, function (item) {
                    that.renderItems.push(item);
                });

                currentPageIndex = params.paging.pageIndex;
                endOfPagedList = (items.length < that.options.pageSize);
            });
        }

        function _renderItems(renderFn, dataItems) {
            // limit number of items rendered in the dropdown
            var dataItemsToRender = _.slice(dataItems, 0, that.options.maxItemsToRender);

            var itemsToRender = _.map(dataItemsToRender, function (data, index) {
                // invoke render callback with the data as parameter
                // this should return an object with a 'label' and 'value' property where
                // 'label' is the template for display and 'value' is the text for the textbox
                // If the object has an 'id' property, it will be used in the 'track by' clause of ng-repeat in the template
                var item = renderFn(data);

                if (!item || !item.hasOwnProperty('label') || !item.hasOwnProperty('value')) {
                    return null;
                }

                // store the data on the renderItem and add to array
                item.data = data;
                // unique 'id' for use in the 'track by' clause
                item.id = item.hasOwnProperty('id') ? item.id : (item.value + item.label + index);

                return item;
            });

            return _.filter(itemsToRender, function (item) {
                return (item !== null);
            });
        }

        function _getRenderFn() {
            // user provided function
            if (angular.isFunction(that.options.renderItem) && that.options.renderItem !== angular.noop) {
                that.itemTemplateLinkFn = null;
                return $q.when(that.options.renderItem.bind(null));
            }

            return _getItemTemplate().then(function (template) {
                that.itemTemplateLinkFn = $compile(template);
                return _getRenderItem.bind(null, template);
            }).catch($exceptionHandler);
        }

        function _getItemTemplate() {
            // itemTemplateUrl
            if (that.options.itemTemplateUrl) {
                return $templateRequest(that.options.itemTemplateUrl);
            }

            // itemTemplate or default
            var template = that.options.itemTemplate || '<span ng-bind-html="entry.item"></span>';
            return $q.when(template);
        }

        function _getRenderItem(template, data) {
            var value = (angular.isObject(data) && that.options.selectedTextAttr) ? data[that.options.selectedTextAttr] : data;
            return {
                value: value,
                label: template
            };
        }

        function _shouldLoadNextPage() {
            return that.options.pagingEnabled && !dataLoadInProgress && !endOfPagedList;
        }

        function _shouldLoadNextPageAtItemIndex(itemIndex) {
            if (!_shouldLoadNextPage()) {
                return false;
            }

            var triggerIndex = that.renderItems.length - that.options.invokePageLoadWhenItemsRemaining - 1;
            return itemIndex >= triggerIndex;
        }

        function _reset() {
            originalSearchText = that.searchText = null;
            currentPageIndex = 0;
            endOfPagedList = false;
        }

        function _setOptions(options) {
            if (_.isEmpty(options)) {
                return;
            }

            angular.forEach(options, function (value, key) {
                if (defaultOptions.hasOwnProperty(key)) {
                    that.options[key] = value;
                }
            });
        }

        var publicApi = (function () {
            return {
                setOptions: _setOptions,
                positionDropdown: _positionDropdownIfVisible,
                hideDropdown: _hideDropdown
            };
        })();
    }

    autoCompleteItemDirective.$inject = ['$compile', '$rootScope', '$sce', '$controller'];
    function autoCompleteItemDirective($compile, $rootScope, $sce, $controller) {
        return {
            restrict: 'E',
            transclude: 'element',
            scope: {},
            controllerAs: 'ctrl',
            bindToController: {
                index: '<',
                renderItem: '<',
                searchText: '<',
                itemTemplateLinkFn: '<'
            },
            controller: function () { },
            link: function (scope, element) {
                var linkFn = null;
                if (_.isFunction(scope.ctrl.itemTemplateLinkFn)) {
                    linkFn = scope.ctrl.itemTemplateLinkFn;
                }
                else {
                    // Needed to maintain backward compatibility since the parameter passed to $compile must be html.
                    // When 'item' is returned from the 'options.renderItem' callback the 'label' might contain
                    // a trusted value [returned by a call to $sce.trustAsHtml(html)]. We can get the original
                    // html that was provided to $sce.trustAsHtml using the valueOf() function.
                    // If 'label' is not a value that had been returned by $sce.trustAsHtml, it will be returned unchanged.
                    var template = $sce.valueOf(scope.ctrl.renderItem.label);
                    linkFn = $compile(template);
                }

                linkFn(createEntryScope(scope), function (clonedElement) {
                    // append to the directive element's parent (<li>) since this directive element is replaced (transclude is set to 'element').
                    $(element[0].parentNode).append(clonedElement);
                });
            }
        };

        function createEntryScope(directiveScope) {
            var entryScope = $rootScope.$new(true);

            // for now its an empty controller. Additional logic can be added to this controller if needed
            var entry = entryScope.entry = $controller(angular.noop);

            var deregisterWatchesFn = _.map(['index', 'renderItem', 'searchText'], function (key) {
                return directiveScope.$watch(('ctrl.' + key), function (newVal) {
                    switch (key) {
                        case 'renderItem':
                            // add 'item' property on entryScope for backward compatibility
                            entry.item = entryScope.item = newVal.data;
                            break;
                        default:
                            entry[key] = newVal;
                            break;
                    }
                });
            });

            helperService.deregisterOnDestroy(directiveScope, deregisterWatchesFn);

            return entryScope;
        }
    }

    autoCompleteNoMatchDirective.$inject = ['$compile', '$rootScope', '$controller'];
    function autoCompleteNoMatchDirective($compile, $rootScope, $controller) {
        return {
            restrict: 'E',
            transclude: 'element',
            scope: {},
            controllerAs: 'ctrl',
            bindToController: {
                template: '<',
                searchText: '<'
            },
            controller: function () { },
            link: function (scope, element) {
                var linkFn = $compile(scope.ctrl.template);
                linkFn(createEntryScope(scope), function (clonedElement) {
                    // append to the directive element's parent (<li>) since this directive element is replaced (transclude is set to 'element').
                    $(element[0].parentNode).append(clonedElement);
                });
            }
        };

        function createEntryScope(directiveScope) {
            var entryScope = $rootScope.$new(true);

            // for now its an empty controller. Additional logic can be added to this controller if needed
            var entry = entryScope.entry = $controller(angular.noop);

            var deregisterFn = directiveScope.$watch('ctrl.searchText', function (newVal) {
                entry.searchText = newVal;
            });

            helperService.deregisterOnDestroy(directiveScope, [deregisterFn]);

            return entryScope;
        }
    }

    function HelperService() {
        var that = this;
        var plugins = [];
        var instanceCount = 0;
        var activeInstanceId = 0;

        this.registerInstance = function (instance) {
            if (instance) {
                plugins.push(instance);
                return ++instanceCount;
            }

            return -1;
        };

        this.setActiveInstanceId = function (instanceId) {
            activeInstanceId = instanceId;
            that.hideAllInactive();
        };

        this.hideAllInactive = function () {
            angular.forEach(plugins, function (ctrl) {
                // hide if this is not the active instance
                if (ctrl.instanceId !== activeInstanceId) {
                    ctrl.autoHide();
                }
            });
        };

        this.deregisterOnDestroy = function (scope, deregisterWatchesFn) {
            // cleanup on destroy
            var destroyFn = scope.$on('$destroy', function () {
                _.each(deregisterWatchesFn, function (deregisterFn) {
                    deregisterFn();
                });

                destroyFn();
            });
        };
    }

    var DOM_EVENT = {
        RESIZE: 'resize',
        SCROLL: 'scroll',
        CLICK: 'click',
        KEYDOWN: 'keydown',
        FOCUS: 'focus',
        INPUT: 'input'
    };

    var KEYCODE = {
        TAB: 9,
        ENTER: 13,
        CTRL: 17,
        ALT: 18,
        ESCAPE: 27,
        LEFTARROW: 37,
        UPARROW: 38,
        RIGHTARROW: 39,
        DOWNARROW: 40,
        MAC_COMMAND_LEFT: 91,
        MAC_COMMAND_RIGHT: 93
    };

    var defaultOptions = {
        /**
         * CSS class applied to the dropdown container.
         * @default null
         */
        containerCssClass: null,
        /**
         * CSS class applied to the selected list element.
         * @default auto-complete-item-selected
         */
        selectedCssClass: 'auto-complete-item-selected',
        /**
         * Minimum number of characters required to display the dropdown.
         * @default 1
         */
        minimumChars: 1,
        /**
         * Maximum number of items to render in the list.
         * @default 20
         */
        maxItemsToRender: 20,
        /**
         * If true displays the dropdown list when the textbox gets focus.
         * @default false
         */
        activateOnFocus: false,
        /**
         * Width in "px" of the dropddown list. This can also be applied using CSS.
         * @default 'auto'
         */
        dropdownWidth: 'auto',
        /**
         * Maximum height in "px" of the dropddown list. This can also be applied using CSS.
         * @default 'auto'
         */
        dropdownHeight: 'auto',
        /**
         * a jQuery object to append the dropddown list.
         * @default null
         */
        dropdownParent: null,
        /**
         * If the data for the dropdown is a collection of objects, this should be the name 
         * of a property on the object. The property value will be used to update the input textbox.
         * @default null
         */
        selectedTextAttr: null,
        /**
         * A template for the dropddown list item. For example "<p ng-bind-html='entry.item.name'></p>";
         * Or using interpolation "<p>{{entry.item.lastName}}, {{entry.item.firstName}}></p>".
         * @default null
         */
        itemTemplate: null,
        /**
         * This is similar to template but the template is loaded from the specified URL, asynchronously.
         * @default null
         */
        itemTemplateUrl: null,
        /**
         * Set to true to enable server side paging. See "data" callback for more information.
         * @default false
         */
        pagingEnabled: false,
        /**
         * The number of items to display per page when paging is enabled.
         * @default 5
         */
        pageSize: 5,
        /**
         * When using the keyboard arrow key to scroll down the list, the "data" callback will 
         * be invoked when at least this many items remain below the current focused item. 
         * Note that dragging the vertical scrollbar to the bottom of the list might also invoke a "data" callback.
         * @default 1
         */
        invokePageLoadWhenItemsRemaining: 1,
        /**
         * Set to true to position the dropdown list using the position() method from the jQueryUI library.
         * See <a href="https://api.jqueryui.com/position/">jQueryUI.position() documentation</a>
         * @default true
         * @bindAsHtml true
         */
        positionUsingJQuery: true,
        /**
         * Options that will be passed to jQueryUI position() method.
         * @default null
         */
        positionUsing: null,
        /**
         * Set to true to let the plugin hide the dropdown list. If this option is set to false you can hide the dropdown list
         * with the hideDropdown() method available in the ready callback.
         * @default true
         */
        autoHideDropdown: true,
        /**
         * Set to true to hide the dropdown list when the window is resized. If this option is set to false you can hide
         * or re-position the dropdown list with the hideDropdown() or positionDropdown() methods available in the ready.
         * callback.
         * @default true
         */
        hideDropdownOnWindowResize: true,
        /**
         * Set to true to enable the template to display a message when no items match the search text.
         * @default true
         */
        noMatchTemplateEnabled: true,
        /**
         * The template used to display the message when no items match the search text.
         * @default "<span>No results match '{{entry.searchText}}'></span>"
         */
        noMatchTemplate: "<span>No results match '{{entry.searchText}}'</span>",
        /**
         * Callback after the plugin is initialized and ready. The callback receives an object with the following methods:
         * @default angular.noop
         */
        ready: angular.noop,
        /**
         * Callback before the "data" callback is invoked.
         * @default angular.noop
         */
        loading: angular.noop,
        /**
         * Callback to get the data for the dropdown. The callback receives the search text as the first parameter.
         * If paging is enabled the callback receives an object with "pageIndex" and "pageSize" properties as the second parameter.
         * This function must return a promise.
         * @default angular.noop
         */
        data: angular.noop,
        /**
         * Callback after the items are rendered in the dropdown
         * @default angular.noop
         */
        loadingComplete: angular.noop,
        /**
         * Callback for custom rendering a list item. This is called for each item in the dropdown.
         * This must return an object literal with "value" and "label" properties where
         * "label" is the template for display and "value" is the text for the textbox.
         * If the object has an "id" property, it will be used in the "track by" clause of the ng-repeat of the dropdown list.
         * @default angular.noop
         */
        renderItem: angular.noop,
        /**
         * Callback after an item is selected from the dropdown. The callback receives an object with an "item" property representing the selected item.
         * @default angular.noop
         */
        itemSelected: angular.noop,
        /**
         * Callback after the dropdown is shown.
         * @default angular.noop
         */
        dropdownShown: angular.noop,
        /**
         * Callback after the dropdown is hidden.
         * @default angular.noop
         */
        dropdownHidden: angular.noop
    };

}));